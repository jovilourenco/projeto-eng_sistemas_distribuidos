package br.engsistemasdistribuidos.reserva.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.engsistemasdistribuidos.reserva.mensageria.ComandoAssento;
import br.engsistemasdistribuidos.reserva.model.ChaveIdempotencia;
import br.engsistemasdistribuidos.reserva.model.EstadoReserva;
import br.engsistemasdistribuidos.reserva.model.Ingresso;
import br.engsistemasdistribuidos.reserva.model.MetodoPagamento;
import br.engsistemasdistribuidos.reserva.model.MotivoCancelamento;
import br.engsistemasdistribuidos.reserva.model.OperacaoIdempotente;
import br.engsistemasdistribuidos.reserva.model.OutboxEvent;
import br.engsistemasdistribuidos.reserva.model.Reserva;
import br.engsistemasdistribuidos.reserva.model.SimulacaoPagamento;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;
import br.engsistemasdistribuidos.reserva.repository.ChaveIdempotenciaRepository;
import br.engsistemasdistribuidos.reserva.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.reserva.repository.ReservaRepository;
import br.engsistemasdistribuidos.reserva.service.ReservaService;

/**
 * Sem @Transactional na classe: os métodos alternam entre Redis (fora de qualquer transação) e
 * gravações no banco, e precisam capturar a violação de unicidade para consultar o banco de novo.
 * Por isso cada gravação usa uma transação própria e curta, aberta pelo {@link TransactionTemplate}.
 */
@Service
public class ReservaServiceImpl implements ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaServiceImpl.class);

    static final String AGREGADO_TIPO = "RESERVA";

    // Resultados do script de trava.
    private static final long TRAVA_EM_DISPUTA = 0;
    private static final long TRAVA_CRIADA = 1;
    private static final long SEM_TRAVA = -1;

    // Gravações do pedido de pagamento que perdem a disputa com outra operação são reavaliadas até 3 vezes.
    private static final int TENTATIVAS_GRAVACAO = 3;


    /*
     * Trava todos os assentos de uma vez, ou nenhum (ADR-003 e ADR-004). O script roda de forma atômica no Redis:
     * nenhuma outra requisição observa os assentos "meio travados", então não há ordem de travamento a respeitar
     * nem travas parciais a desfazer. Uma trava que já é desta mesma compra conta como livre.
     *   0: algum assento está travado por outra compra; nada é alterado.
     *   1: todos estavam livres e agora estão travados para esta compra, com validade ARGV[2] ms.
     *   2: idem, mas algum já era desta compra (repetição em andamento, ou trava que sobrou de uma falha).
     */
    private static final RedisScript<Long> TRAVAR_ASSENTOS = new DefaultRedisScript<>("""
            local propria = false
            for _, chave in ipairs(KEYS) do
              local dono = redis.call('get', chave)
              if dono then
                if dono ~= ARGV[1] then return 0 end
                propria = true
              end
            end
            for _, chave in ipairs(KEYS) do
              redis.call('set', chave, ARGV[1], 'PX', ARGV[2])
            end
            if propria then return 2 end
            return 1
            """, Long.class);

    // "Comparar e remover": só apaga as travas que pertencem à compra informada (ADR-003).
    private static final RedisScript<Long> LIBERAR_ASSENTOS = new DefaultRedisScript<>("""
            local removidas = 0
            for _, chave in ipairs(KEYS) do
              if redis.call('get', chave) == ARGV[1] then
                removidas = removidas + redis.call('del', chave)
              end
            end
            return removidas
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ReservaRepository reservaRepository;
    private final OutboxEventRepository outboxRepository;
    private final ChaveIdempotenciaRepository chaveRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Duration prazoPagamento;

    public ReservaServiceImpl(StringRedisTemplate redisTemplate,
                              ReservaRepository reservaRepository,
                              OutboxEventRepository outboxRepository,
                              ChaveIdempotenciaRepository chaveRepository,
                              TransactionTemplate transactionTemplate,
                              ObjectMapper objectMapper,
                              @Value("${reserva.prazo-pagamento:PT9M}") Duration prazoPagamento) {
        this.redisTemplate = redisTemplate;
        this.reservaRepository = reservaRepository;
        this.outboxRepository = outboxRepository;
        this.chaveRepository = chaveRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.prazoPagamento = prazoPagamento;
    }

    // ------------------------------------------------------------------ criação (ADR-002, item 2)

    @Override
    public Criacao criarReserva(String chaveIdempotencia, Integer idEvento, List<Integer> idAssentos,
                                Integer idUsuario, TipoIngresso tipoIngresso) {
        // O id da compra vem do usuário + chave: a mesma requisição repetida sempre gera o mesmo id.
        UUID idCompra = derivarIdCompra(idUsuario, chaveIdempotencia);
        List<Integer> assentos = idAssentos.stream().sorted().toList();
        String hash = hashRequisicao(idEvento, assentos, tipoIngresso);

        // 1. A compra já existe? Então é uma repetição: devolve (mesmo corpo) ou 422 (corpo diferente).
        //    Em nenhum dos casos o Redis é acessado.
        var existente = reservaRepository.findComIngressosById(idCompra);
        if (existente.isPresent()) {
            return new Criacao(conferirRepeticao(existente.get(), hash), false);
        }

        // 2. Travar todos os assentos para esta compra (mesma validade do prazo de pagamento - ADR-003).
        List<String> chaves = assentos.stream().map(ReservaServiceImpl::chaveTrava).toList();
        String dono = idCompra.toString();
        long trava = travar(chaves, dono);

        if (trava == TRAVA_EM_DISPUTA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assento já está em processo de reserva por outro usuário.");
        }
        // Só esta requisição criou as travas (resultado 1) pode removê-las se não gravar a compra. Com o resultado 2,
        // uma requisição igual pode estar gravando agora e usando as mesmas travas.
        boolean travouSozinha = trava == TRAVA_CRIADA;

        // 3. Gravar reserva, ingressos e Outbox numa transação. Se uma requisição igual estiver gravando, o banco
        //    segura este INSERT até ela terminar: se ela gravou, a chave primária duplicada devolve a reserva dela.
        try {
            Reserva criada = transactionTemplate.execute(status ->
                    gravarCompra(idCompra, chaveIdempotencia, hash, idEvento, assentos, idUsuario, tipoIngresso));
            return new Criacao(criada, true);
        } catch (DataIntegrityViolationException e) {
            // Id já existe: outra requisição com a mesma chave gravou primeiro. As travas dos assentos dessa compra
            // ficam; as dos assentos que só esta requisição pediu (mesma chave com corpo diferente) são removidas.
            var gravada = reservaRepository.findComIngressosById(idCompra);
            if (gravada.isEmpty()) {
                // A violação não foi do id (não há gêmea): é uma falha comum.
                if (travouSozinha) {
                    liberar(chaves, dono);
                }
                throw e;
            }
            List<Integer> assentosDaGravada = gravada.get().getIngressos().stream()
                    .map(Ingresso::getIdAssento).toList();
            List<String> sobrando = assentos.stream().filter(id -> !assentosDaGravada.contains(id))
                    .map(ReservaServiceImpl::chaveTrava).toList();
            if (!sobrando.isEmpty()) {
                liberar(sobrando, dono);
            }
            return new Criacao(conferirRepeticao(gravada.get(), hash), false);
        } catch (RuntimeException e) {
            if (travouSozinha) {
                liberar(chaves, dono);
            }
            throw e;
        }
    }

    private Reserva gravarCompra(UUID idCompra, String chave, String hash, Integer idEvento, List<Integer> assentos,
                                 Integer idUsuario, TipoIngresso tipoIngresso) {
        LocalDateTime agora = LocalDateTime.now();

        Reserva reserva = new Reserva();
        reserva.setId(idCompra);
        reserva.setChaveIdempotencia(chave);
        reserva.setHashRequisicao(hash);
        reserva.setIdEvento(idEvento);
        reserva.setIdUsuario(idUsuario);
        reserva.setEstado(EstadoReserva.INICIADA);
        reserva.setDataReserva(agora);
        reserva.setDataExpiracao(agora.plus(prazoPagamento));
        for (Integer idAssento : assentos) {
            Ingresso ingresso = new Ingresso();
            // Id derivado da compra e do assento: estável, e único porque cada assento aparece uma vez na compra.
            ingresso.setId(UUID.nameUUIDFromBytes((idCompra + ":" + idAssento).getBytes(StandardCharsets.UTF_8)));
            ingresso.setIdAssento(idAssento);
            ingresso.setTipoIngresso(tipoIngresso);
            reserva.adicionarIngresso(ingresso);
        }

        // saveAndFlush: o INSERT vai ao banco aqui, para que um id duplicado falhe dentro desta transação.
        Reserva salva = reservaRepository.saveAndFlush(reserva);
        // Estado INICIADA: envia assento.reservar (ADR-002, item 3).
        enviarComando(ComandoAssento.RESERVAR, salva);
        return salva;
    }

    private Reserva conferirRepeticao(Reserva existente, String hash) {
        if (!existente.getHashRequisicao().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Esta Idempotency-Key já foi usada com uma requisição diferente.");
        }
        return existente;
    }

    // ------------------------------------------------------------------ pedido de pagamento

    @Override
    public Reserva solicitarPagamento(Reserva reserva, String chaveIdempotencia, MetodoPagamento metodo,
                                      SimulacaoPagamento simulacao) {
        String hash = hashPagamento(metodo, simulacao);
        Reserva atual = reserva;

        // O pedido é aceito também em INICIADA, antes de o Serviço de Eventos e Assentos confirmar os assentos,
        // porque o cliente paga logo depois de reservar (contrato do kit de avaliação). O pedido fica registrado,
        // e o orquestrador só o processa quando a compra chegar a AGUARDANDO_PAGAMENTO, com o valor total
        // conhecido (issue #50).
        for (int tentativa = 1; ; tentativa++) {
            // Repetição do mesmo pedido: devolve o estado atual, sem registrar de novo.
            if (chaveIdempotencia.equals(atual.getChavePagamento())) {
                return conferirRepeticaoPagamento(atual, hash);
            }
            // Já existe um pedido em aberto (com outra chave) ou um pagamento em processamento: não gera outra
            // cobrança e devolve o estado atual (ADR-002, item 5: "uma tentativa, uma chamada").
            if (atual.isPagamentoSolicitado() || atual.getEstado() == EstadoReserva.PROCESSANDO_PAGAMENTO) {
                return atual;
            }
            exigirPagavel(atual);

            Reserva aGravar = atual;
            try {
                return transactionTemplate.execute(status -> {
                    aGravar.registrarPedidoPagamento(chaveIdempotencia, hash, metodo, simulacao, LocalDateTime.now());
                    // O @Version confere que ninguém alterou a compra desde que ela foi lida.
                    return reservaRepository.saveAndFlush(aGravar);
                });
            } catch (ObjectOptimisticLockingFailureException e) {
                // Outra operação alterou a compra ao mesmo tempo; por exemplo, o orquestrador recebeu
                // assento.reservado logo depois da criação. O pedido é reavaliado sobre o estado novo
                // (ADR-002, item 4, regra 2), em vez de recusado.
                if (tentativa == TENTATIVAS_GRAVACAO) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A reserva foi alterada por outra operação. Consulte-a novamente.");
                }
                atual = reservaRepository.findComIngressosById(reserva.getId()).orElseThrow();
            }
        }
    }

    private static void exigirPagavel(Reserva reserva) {
        EstadoReserva estado = reserva.getEstado();
        if (estado == EstadoReserva.CONFIRMANDO || estado == EstadoReserva.CONCLUIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A reserva já foi paga.");
        }
        if (!estado.aguardandoUsuario()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A reserva foi cancelada.");
        }
        // O pagamento só é aceito dentro do prazo (ADR-002, item 3), mesmo antes de a expiração ser processada.
        if (!LocalDateTime.now().isBefore(reserva.getDataExpiracao())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O prazo para pagamento da reserva terminou.");
        }
    }

    private static Reserva conferirRepeticaoPagamento(Reserva reserva, String hash) {
        if (!reserva.getHashPagamento().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Esta Idempotency-Key já foi usada com uma requisição diferente.");
        }
        return reserva;
    }

    // ------------------------------------------------------------------ cancelamento

    @Override
    public Reserva cancelarReserva(Reserva reserva, String chaveIdempotencia) {
        UUID id = reserva.getId();

        // Cancelamento já aplicado com esta chave: não aplica de novo, devolve o estado atual.
        if (chaveJaUsada(id, chaveIdempotencia)) {
            return reserva;
        }
        // Só antes do pagamento: depois de pedido, o pagamento pode já estar em curso, e não há reembolso
        // (ADR-002, item 4, regras 1, 4 e 7).
        if (!reserva.getEstado().aguardandoUsuario() || reserva.isPagamentoSolicitado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Apenas reservas aguardando pagamento, sem pagamento solicitado, podem ser canceladas.");
        }

        try {
            // Chave, mudança de estado e Outbox na mesma transação (ADR-002).
            return transactionTemplate.execute(status -> {
                chaveRepository.save(new ChaveIdempotencia(id, OperacaoIdempotente.CANCELAMENTO, chaveIdempotencia));
                reserva.setEstado(EstadoReserva.COMPENSANDO);
                reserva.setMotivoCancelamento(MotivoCancelamento.CANCELADA_PELO_USUARIO);
                Reserva salva = reservaRepository.saveAndFlush(reserva);
                // COMPENSANDO: envia assento.liberar. A trava do Redis só sai em CANCELADA, quando o Serviço
                // de Eventos e Assentos confirmar a liberação (ADR-002, item 3).
                enviarComando(ComandoAssento.LIBERAR, salva);
                return salva;
            });
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            // Outra requisição alterou a compra ao mesmo tempo. Se foi esta mesma chave, é repetição.
            if (chaveJaUsada(id, chaveIdempotencia)) {
                return reservaRepository.findComIngressosById(id).orElseThrow();
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A reserva foi alterada por outra operação. Consulte-a novamente.");
        }
    }

    private boolean chaveJaUsada(UUID idReserva, String chave) {
        return chaveRepository.existsByIdReservaAndOperacaoAndChave(idReserva, OperacaoIdempotente.CANCELAMENTO, chave);
    }

    // ------------------------------------------------------------------ auxiliares

    // Grava o comando no Outbox, na transação corrente. O relay publica no RabbitMQ (ADR-001).
    private void enviarComando(String tipo, Reserva reserva) {
        List<Integer> assentos = reserva.getIngressos().stream().map(Ingresso::getIdAssento).toList();
        ComandoAssento comando = ComandoAssento.novo(reserva.getId(), reserva.getIdEvento(), assentos);
        try {
            outboxRepository.save(new OutboxEvent(AGREGADO_TIPO, reserva.getId().toString(), tipo,
                    objectMapper.writeValueAsString(comando)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o comando " + tipo, e);
        }
    }

    /*
     * Redis fora do ar: a reserva segue sem a trava (ADR-003, "falha aberta"). A trava é só um filtro de
     * admissão; quem decide o dono do assento é o banco do Serviço de Eventos e Assentos, e a disputa passa
     * a ser resolvida lá, de forma assíncrona (resposta assento.indisponivel).
     */
    private long travar(List<String> chaves, String dono) {
        try {
            Long resultado = redisTemplate.execute(TRAVAR_ASSENTOS, chaves, dono,
                    String.valueOf(prazoPagamento.toMillis()));
            return resultado == null ? TRAVA_EM_DISPUTA : resultado;
        } catch (DataAccessException e) {
            log.warn("Redis indisponível; reserva {} segue sem trava (ADR-003)", dono, e);
            return SEM_TRAVA;
        }
    }

    private void liberar(List<String> chaves, String dono) {
        try {
            redisTemplate.execute(LIBERAR_ASSENTOS, chaves, dono);
        } catch (DataAccessException e) {
            // As travas expiram sozinhas no fim do prazo.
            log.warn("Redis indisponível; travas da reserva {} expiram pela validade", dono, e);
        }
    }

    public static UUID derivarIdCompra(Integer idUsuario, String chaveIdempotencia) {
        // UUID baseado em nome (versão 3): o mesmo texto sempre gera o mesmo UUID.
        return UUID.nameUUIDFromBytes((idUsuario + ":" + chaveIdempotencia).getBytes(StandardCharsets.UTF_8));
    }

    // Resumo dos campos do corpo (e não do texto JSON), para que espaços, a ordem dos campos e a ordem
    // dos assentos não contem.
    public static String hashRequisicao(Integer idEvento, List<Integer> idAssentosOrdenados, TipoIngresso tipo) {
        return sha256(idEvento + "|" + idAssentosOrdenados + "|" + tipo);
    }

    static String hashPagamento(MetodoPagamento metodo, SimulacaoPagamento simulacao) {
        return sha256(metodo + "|" + simulacao);
    }

    private static String sha256(String conteudo) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(conteudo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    static String chaveTrava(Integer idAssento) {
        return "assento:trava:" + idAssento;
    }
}
