package br.engsistemasdistribuidos.reserva.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
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
import br.engsistemasdistribuidos.reserva.service.impl.ReservaServiceImpl;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
public class ReservaServiceImplTests {

    private static final Duration PRAZO = Duration.ofMinutes(9);
    private static final String LOCK_KEY = "assento:lock:10";
    private static final String CHAVE = "chave-1";
    private static final int USUARIO = 20;
    private static final UUID ID_COMPRA = ReservaServiceImpl.derivarIdCompra(USUARIO, CHAVE);
    private static final String DONO = ID_COMPRA.toString();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private IngressoRepository ingressoRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private ChaveIdempotenciaRepository chaveRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ReservaServiceImpl service;

    @BeforeEach
    void setUp() {
        // Construção manual: o prazo (Duration) não pode ser injetado pelo @InjectMocks.
        service = new ReservaServiceImpl(redisTemplate, ingressoRepository, outboxRepository,
                chaveRepository, transactionTemplate, PRAZO);
    }

    // O TransactionTemplate simulado só executa o bloco, sem transação de verdade.
    private void executarTransacoes() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
    }

    private Ingresso criar(TipoIngresso tipo) {
        return service.criarReserva(CHAVE, 1, 10, USUARIO, tipo);
    }

    // Um ingresso já gravado por uma requisição anterior com a mesma chave e o corpo informado.
    private Ingresso existente(TipoIngresso tipoDoCorpoOriginal) {
        Ingresso ingresso = new Ingresso();
        ingresso.setId(ID_COMPRA);
        ingresso.setHashRequisicao(ReservaServiceImpl.hashRequisicao(1, 10, tipoDoCorpoOriginal));
        ingresso.setStatus(StatusIngresso.PENDENTE_PAGAMENTO);
        return ingresso;
    }

    // ------------------------------------------------------------------ id da compra

    @Test
    void derivarIdCompra_mesmoUsuarioEChave_geraSempreOMesmoId() {
        assertThat(ReservaServiceImpl.derivarIdCompra(USUARIO, CHAVE)).isEqualTo(ID_COMPRA);
    }

    @Test
    void derivarIdCompra_outroUsuarioComMesmaChave_geraIdDiferente() {
        // Dois usuários podem, por acaso, gerar a mesma chave: as compras não podem se misturar.
        assertThat(ReservaServiceImpl.derivarIdCompra(USUARIO + 1, CHAVE)).isNotEqualTo(ID_COMPRA);
    }

    // ------------------------------------------------------------------ criação

    @Test
    void criar_novaCompra_travaAssentoGravaIngressoEOutbox() {
        executarTransacoes();
        when(ingressoRepository.findById(ID_COMPRA)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(LOCK_KEY, DONO, PRAZO)).thenReturn(true);
        when(ingressoRepository.saveAndFlush(any(Ingresso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ingresso ingresso = criar(TipoIngresso.INTEIRA);

        assertThat(ingresso.getId()).isEqualTo(ID_COMPRA);
        assertThat(ingresso.getIdUsuario()).isEqualTo(USUARIO);
        assertThat(ingresso.getStatus()).isEqualTo(StatusIngresso.PENDENTE_PAGAMENTO);
        assertThat(ingresso.getDataExpiracao()).isEqualTo(ingresso.getDataReserva().plus(PRAZO));
        assertThat(ingresso.getHashRequisicao()).hasSize(64);

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getAgregadoTipo()).isEqualTo("INGRESSO");
        assertThat(captor.getValue().getAgregadoId()).isEqualTo(DONO);
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("RESERVA_CRIADA");
        assertThat(captor.getValue().getPayload()).contains(DONO).contains("\"idAssento\": 10");
        assertThat(captor.getValue().isProcessado()).isFalse();

        verify(redisTemplate, never()).execute(any(RedisScript.class), any(List.class), any());
    }

    @Test
    void criar_mesmaChaveMesmoCorpo_devolveCompraExistenteSemAcessarRedis() {
        Ingresso original = existente(TipoIngresso.INTEIRA);
        when(ingressoRepository.findById(ID_COMPRA)).thenReturn(Optional.of(original));

        assertSame(original, criar(TipoIngresso.INTEIRA));

        verifyNoInteractions(redisTemplate, outboxRepository);
    }

    @Test
    void criar_mesmaChaveCorpoDiferente_retorna422SemAcessarRedis() {
        Ingresso original = existente(TipoIngresso.INTEIRA);
        when(ingressoRepository.findById(ID_COMPRA)).thenReturn(Optional.of(original));

        var ex = assertThrows(ResponseStatusException.class, () -> criar(TipoIngresso.ESTUDANTE));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(redisTemplate, outboxRepository);
    }

    @Test
    void criar_assentoTravadoPorOutraCompra_retorna409() {
        when(ingressoRepository.findById(ID_COMPRA)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq(DONO), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(LOCK_KEY)).thenReturn(UUID.randomUUID().toString());

        var ex = assertThrows(ResponseStatusException.class, () -> criar(TipoIngresso.INTEIRA));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getReason()).contains("outro usuário");
        verifyNoInteractions(transactionTemplate, outboxRepository);
    }

    @Test
    void criar_mesmaRequisicaoEmAndamento_retorna409EmProcessamento() {
        // A trava é desta mesma compra, mas ela ainda não foi gravada.
        when(ingressoRepository.findById(ID_COMPRA)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq(DONO), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(LOCK_KEY)).thenReturn(DONO);

        var ex = assertThrows(ResponseStatusException.class, () -> criar(TipoIngresso.INTEIRA));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getReason()).contains("processamento");
        verifyNoInteractions(transactionTemplate);
        // A trava pertence à outra requisição em andamento: não pode ser removida.
        verify(redisTemplate, never()).execute(any(RedisScript.class), any(List.class), any());
    }

    @Test
    void criar_idDuplicadoAoGravar_devolveCompraDaOutraRequisicaoELiberaTrava() {
        Ingresso gravadaPelaOutra = existente(TipoIngresso.INTEIRA);
        when(ingressoRepository.findById(ID_COMPRA))
                .thenReturn(Optional.empty())                 // passo 1: ainda não existia
                .thenReturn(Optional.of(gravadaPelaOutra));   // depois da violação: existe
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(LOCK_KEY, DONO, PRAZO)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenThrow(new DataIntegrityViolationException("id duplicado"));

        assertSame(gravadaPelaOutra, criar(TipoIngresso.INTEIRA));

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq(DONO));
    }

    @Test
    void criar_erroAoGravarOutbox_liberaTravaERelancaErro() {
        executarTransacoes();
        when(ingressoRepository.findById(ID_COMPRA)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(LOCK_KEY, DONO, PRAZO)).thenReturn(true);
        when(ingressoRepository.saveAndFlush(any(Ingresso.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any(OutboxEvent.class))).thenThrow(new RuntimeException("Erro no banco de dados"));

        assertThrows(RuntimeException.class, () -> criar(TipoIngresso.INTEIRA));

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq(DONO));
    }

    // ------------------------------------------------------------------ cancelamento

    private Ingresso pendente() {
        Ingresso ingresso = new Ingresso();
        ingresso.setId(ID_COMPRA);
        ingresso.setIdEvento(1);
        ingresso.setIdAssento(10);
        ingresso.setStatus(StatusIngresso.PENDENTE_PAGAMENTO);
        return ingresso;
    }

    @Test
    void cancelar_registraChaveCancelaGravaOutboxELiberaTrava() {
        executarTransacoes();
        Ingresso ingresso = pendente();
        when(chaveRepository.existsByIdIngressoAndOperacaoAndChave(ID_COMPRA, OperacaoIdempotente.CANCELAMENTO, CHAVE))
                .thenReturn(false);
        when(ingressoRepository.saveAndFlush(ingresso)).thenReturn(ingresso);

        Ingresso cancelado = service.cancelarReserva(ingresso, CHAVE);

        assertThat(cancelado.getStatus()).isEqualTo(StatusIngresso.CANCELADO);
        var chaveCaptor = ArgumentCaptor.forClass(ChaveIdempotencia.class);
        verify(chaveRepository).save(chaveCaptor.capture());
        assertThat(chaveCaptor.getValue().getChave()).isEqualTo(CHAVE);
        assertThat(chaveCaptor.getValue().getIdIngresso()).isEqualTo(ID_COMPRA);

        var outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTipoEvento()).isEqualTo("RESERVA_CANCELADA");

        // Trava removida com "comparar e remover", usando o id da compra como dono.
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq(DONO));
    }

    @Test
    void cancelar_mesmaChaveRepetida_devolveEstadoAtualSemAplicarDeNovo() {
        Ingresso jaCancelado = pendente();
        jaCancelado.setStatus(StatusIngresso.CANCELADO);
        when(chaveRepository.existsByIdIngressoAndOperacaoAndChave(ID_COMPRA, OperacaoIdempotente.CANCELAMENTO, CHAVE))
                .thenReturn(true);

        assertSame(jaCancelado, service.cancelarReserva(jaCancelado, CHAVE));

        verifyNoInteractions(transactionTemplate, outboxRepository, redisTemplate);
    }

    @Test
    void cancelar_ingressoPago_retorna409() {
        Ingresso pago = pendente();
        pago.setStatus(StatusIngresso.PAGO);
        when(chaveRepository.existsByIdIngressoAndOperacaoAndChave(any(), any(), anyString())).thenReturn(false);

        var ex = assertThrows(ResponseStatusException.class, () -> service.cancelarReserva(pago, CHAVE));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(transactionTemplate, outboxRepository, redisTemplate);
    }
}
