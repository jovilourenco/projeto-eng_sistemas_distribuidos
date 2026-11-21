package br.engsistemasdistribuidos.reserva.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.engsistemasdistribuidos.reserva.model.ChaveIdempotencia;
import br.engsistemasdistribuidos.reserva.model.Ingresso;
import br.engsistemasdistribuidos.reserva.model.OperacaoIdempotente;
import br.engsistemasdistribuidos.reserva.model.OutboxEvent;
import br.engsistemasdistribuidos.reserva.model.StatusIngresso;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;
import br.engsistemasdistribuidos.reserva.repository.ChaveIdempotenciaRepository;
import br.engsistemasdistribuidos.reserva.repository.IngressoRepository;
import br.engsistemasdistribuidos.reserva.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.reserva.service.ReservaService;

/**
 * Sem @Transactional na classe: os métodos alternam entre Redis (fora de qualquer transação) e
 * gravações no banco, e precisam capturar a violação de unicidade para consultar o banco de novo.
 * Por isso cada gravação usa uma transação própria e curta, aberta pelo {@link TransactionTemplate}.
 */
@Service
public class ReservaServiceImpl implements ReservaService {

    static final String AGREGADO_TIPO = "INGRESSO";
    static final String RESERVA_CRIADA = "RESERVA_CRIADA";
    static final String RESERVA_CANCELADA = "RESERVA_CANCELADA";

    // "Comparar e remover": só apaga a trava se ela pertencer à compra informada (ADR-003).
    private static final RedisScript<Long> LIBERAR_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final IngressoRepository ingressoRepository;
    private final OutboxEventRepository outboxRepository;
    private final ChaveIdempotenciaRepository chaveRepository;
    private final TransactionTemplate transactionTemplate;
    private final Duration prazoPagamento;

    public ReservaServiceImpl(StringRedisTemplate redisTemplate,
                              IngressoRepository ingressoRepository,
                              OutboxEventRepository outboxRepository,
                              ChaveIdempotenciaRepository chaveRepository,
                              TransactionTemplate transactionTemplate,
                              @Value("${reserva.prazo-pagamento:PT9M}") Duration prazoPagamento) {
        this.redisTemplate = redisTemplate;
        this.ingressoRepository = ingressoRepository;
        this.outboxRepository = outboxRepository;
        this.chaveRepository = chaveRepository;
        this.transactionTemplate = transactionTemplate;
        this.prazoPagamento = prazoPagamento;
    }

    // ------------------------------------------------------------------ criação (ADR-002, item 2)

    @Override
    public Ingresso criarReserva(String chaveIdempotencia, Integer idEvento, Integer idAssento,
                                 Integer idUsuario, TipoIngresso tipoIngresso) {
        // O id da compra vem do usuário + chave: a mesma requisição repetida sempre gera o mesmo id.
        UUID idCompra = derivarIdCompra(idUsuario, chaveIdempotencia);
        String hash = hashRequisicao(idEvento, idAssento, tipoIngresso);

        // 1. A compra já existe? Então é uma repetição: devolve (mesmo corpo) ou 422 (corpo diferente).
        //    Em nenhum dos casos o Redis é acessado.
        var existente = ingressoRepository.findById(idCompra);
        if (existente.isPresent()) {
            return conferirRepeticao(existente.get(), hash);
        }

        // 2. Tentar travar o assento para esta compra (mesma validade do prazo de pagamento - ADR-003).
        String lockKey = lockKey(idAssento);
        String dono = idCompra.toString();
        Boolean lockAdquirido = redisTemplate.opsForValue().setIfAbsent(lockKey, dono, prazoPagamento);

        if (Boolean.FALSE.equals(lockAdquirido)) {
            if (dono.equals(redisTemplate.opsForValue().get(lockKey))) {
                // A trava é desta mesma compra: outra requisição igual está em andamento.
                return ingressoRepository.findById(idCompra)
                        .map(ingresso -> conferirRepeticao(ingresso, hash))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "Requisição em processamento. Tente novamente em instantes."));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assento já está em processo de reserva por outro usuário.");
        }

        // 3. Gravar ingresso + Outbox numa transação. Daqui em diante, se esta requisição não gravar
        //    a compra, a trava que ela criou é removida.
        try {
            return transactionTemplate.execute(status ->
                    gravarCompra(idCompra, hash, idEvento, idAssento, idUsuario, tipoIngresso));
        } catch (DataIntegrityViolationException e) {
            // Id já existe: outra requisição com a mesma chave gravou primeiro.
            liberarLock(lockKey, dono);
            return ingressoRepository.findById(idCompra)
                    .map(ingresso -> conferirRepeticao(ingresso, hash))
                    .orElseThrow(() -> e);
        } catch (RuntimeException e) {
            liberarLock(lockKey, dono);
            throw e;
        }
    }

    private Ingresso gravarCompra(UUID idCompra, String hash, Integer idEvento, Integer idAssento,
                                  Integer idUsuario, TipoIngresso tipoIngresso) {
        LocalDateTime agora = LocalDateTime.now();

        Ingresso ingresso = new Ingresso();
        ingresso.setId(idCompra);
        ingresso.setHashRequisicao(hash);
        ingresso.setIdEvento(idEvento);
        ingresso.setIdAssento(idAssento);
        ingresso.setIdUsuario(idUsuario);
        ingresso.setTipoIngresso(tipoIngresso);
        ingresso.setStatus(StatusIngresso.PENDENTE_PAGAMENTO);
        ingresso.setDataReserva(agora);
        ingresso.setDataExpiracao(agora.plus(prazoPagamento));

        // saveAndFlush: o INSERT vai ao banco aqui, para que um id duplicado falhe dentro desta transação.
        Ingresso salvo = ingressoRepository.saveAndFlush(ingresso);
        outboxRepository.save(new OutboxEvent(AGREGADO_TIPO, idCompra.toString(), RESERVA_CRIADA, payload(salvo)));
        return salvo;
    }

    private Ingresso conferirRepeticao(Ingresso existente, String hash) {
        if (!existente.getHashRequisicao().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Esta Idempotency-Key já foi usada com uma requisição diferente.");
        }
        return existente;
    }

    // ------------------------------------------------------------------ cancelamento

    @Override
    public Ingresso cancelarReserva(Ingresso ingresso, String chaveIdempotencia) {
        UUID id = ingresso.getId();

        // Cancelamento já aplicado com esta chave: não aplica de novo, devolve o estado atual.
        if (chaveJaUsada(id, chaveIdempotencia)) {
            return ingresso;
        }
        // Pago não pode ser cancelado: não há reembolso (ADR-002, item 7).
        if (ingresso.getStatus() != StatusIngresso.PENDENTE_PAGAMENTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Apenas ingressos pendentes de pagamento podem ser cancelados.");
        }

        Ingresso cancelado;
        try {
            // Chave, mudança de status e Outbox na mesma transação (ADR-002).
            cancelado = transactionTemplate.execute(status -> {
                chaveRepository.save(new ChaveIdempotencia(id, OperacaoIdempotente.CANCELAMENTO, chaveIdempotencia));
                ingresso.setStatus(StatusIngresso.CANCELADO);
                // O @Version confere que ninguém alterou o ingresso desde que ele foi lido.
                Ingresso salvo = ingressoRepository.saveAndFlush(ingresso);
                outboxRepository.save(new OutboxEvent(AGREGADO_TIPO, id.toString(), RESERVA_CANCELADA, payload(salvo)));
                return salvo;
            });
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            // Outra requisição alterou o ingresso ao mesmo tempo. Se foi esta mesma chave, é repetição.
            if (chaveJaUsada(id, chaveIdempotencia)) {
                return ingressoRepository.findById(id).orElseThrow();
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O ingresso foi alterado por outra operação. Consulte-o novamente.");
        }

        // Só depois do commit: a trava não é liberada se o cancelamento não foi gravado.
        liberarLock(lockKey(cancelado.getIdAssento()), id.toString());
        return cancelado;
    }

    private boolean chaveJaUsada(UUID idIngresso, String chave) {
        return chaveRepository.existsByIdIngressoAndOperacaoAndChave(
                idIngresso, OperacaoIdempotente.CANCELAMENTO, chave);
    }

    // ------------------------------------------------------------------ auxiliares

    private void liberarLock(String lockKey, String dono) {
        redisTemplate.execute(LIBERAR_LOCK, List.of(lockKey), dono);
    }

    public static UUID derivarIdCompra(Integer idUsuario, String chaveIdempotencia) {
        // UUID baseado em nome (versão 3): o mesmo texto sempre gera o mesmo UUID.
        return UUID.nameUUIDFromBytes((idUsuario + ":" + chaveIdempotencia).getBytes(StandardCharsets.UTF_8));
    }

    public static String hashRequisicao(Integer idEvento, Integer idAssento, TipoIngresso tipoIngresso) {
        // Resumo dos campos do corpo (e não do texto JSON), para que espaços ou ordem dos campos não contem.
        String conteudo = idEvento + "|" + idAssento + "|" + tipoIngresso;
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(conteudo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static String lockKey(Integer idAssento) {
        return "assento:lock:" + idAssento;
    }

    private static String payload(Ingresso ingresso) {
        return "{\"idIngresso\": \"" + ingresso.getId()
                + "\", \"idEvento\": " + ingresso.getIdEvento()
                + ", \"idAssento\": " + ingresso.getIdAssento() + "}";
    }
}
