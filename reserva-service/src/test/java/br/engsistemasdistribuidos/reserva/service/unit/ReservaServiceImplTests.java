package br.engsistemasdistribuidos.reserva.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.engsistemasdistribuidos.reserva.model.EstadoReserva;
import br.engsistemasdistribuidos.reserva.model.Ingresso;
import br.engsistemasdistribuidos.reserva.model.MetodoPagamento;
import br.engsistemasdistribuidos.reserva.model.Reserva;
import br.engsistemasdistribuidos.reserva.model.SimulacaoPagamento;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;
import br.engsistemasdistribuidos.reserva.repository.ChaveIdempotenciaRepository;
import br.engsistemasdistribuidos.reserva.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.reserva.repository.ReservaRepository;
import br.engsistemasdistribuidos.reserva.service.ReservaService;
import br.engsistemasdistribuidos.reserva.service.impl.ReservaServiceImpl;

/**
 * Caminhos difíceis de provocar com Redis e banco reais: Redis fora do ar, corrida entre duas requisições
 * iguais e alterações simultâneas na mesma reserva. Os caminhos comuns estão no ReservaIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ReservaServiceImplTests {

    private static final Duration PRAZO = Duration.ofMinutes(9);
    private static final String CHAVE = "chave-1";
    private static final int USUARIO = 20;
    private static final List<Integer> ASSENTOS = List.of(10, 11);
    private static final UUID ID_COMPRA = ReservaServiceImpl.derivarIdCompra(USUARIO, CHAVE);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ReservaRepository reservaRepository;

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
        service = new ReservaServiceImpl(redisTemplate, reservaRepository, outboxRepository, chaveRepository,
                transactionTemplate, new ObjectMapper().registerModule(new JavaTimeModule()), PRAZO);
    }

    // O TransactionTemplate simulado só executa o bloco, sem transação de verdade.
    private void executarTransacoes() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
    }

    private void resultadoDaTrava(Long resultado) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(resultado);
    }

    private ReservaService.Criacao criar() {
        return service.criarReserva(CHAVE, 1, ASSENTOS, USUARIO, TipoIngresso.INTEIRA);
    }

    private static Reserva reservaGravada() {
        Reserva reserva = new Reserva();
        reserva.setId(ID_COMPRA);
        reserva.setIdEvento(1);
        reserva.setIdUsuario(USUARIO);
        reserva.setEstado(EstadoReserva.INICIADA);
        reserva.setHashRequisicao(ReservaServiceImpl.hashRequisicao(1, ASSENTOS, TipoIngresso.INTEIRA));
        reserva.setDataReserva(LocalDateTime.now());
        reserva.setDataExpiracao(LocalDateTime.now().plus(PRAZO));
        return reserva;
    }

    // ------------------------------------------------------------------ id e resumo

    @Test
    void derivarIdCompra_mesmoUsuarioEChaveGeraOMesmoId_outroUsuarioGeraOutro() {
        assertThat(ReservaServiceImpl.derivarIdCompra(USUARIO, CHAVE)).isEqualTo(ID_COMPRA);
        assertThat(ReservaServiceImpl.derivarIdCompra(USUARIO + 1, CHAVE)).isNotEqualTo(ID_COMPRA);
    }

    // ------------------------------------------------------------------ criação

    @Test
    void criar_redisForaDoAr_segueSemTravaEGravaAReserva() {
        // ADR-003: a trava é só um filtro de admissão; sem ela, a disputa é resolvida no Eventos e Assentos.
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.empty());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("fora do ar"));
        executarTransacoes();
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        ReservaService.Criacao criacao = criar();

        assertThat(criacao.nova()).isTrue();
        assertThat(criacao.reserva().getIngressos()).extracting(i -> i.getIdAssento()).containsExactly(10, 11);
        verify(outboxRepository).save(any());
    }

    @Test
    void criar_falhaAoGravar_liberaAsTravasCriadasPorEstaRequisicao() {
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.empty());
        resultadoDaTrava(1L);
        executarTransacoes();
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(new IllegalStateException("banco"));

        assertThatThrownBy(this::criar).isInstanceOf(IllegalStateException.class);

        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("assento:trava:10", "assento:trava:11")), eq(ID_COMPRA.toString()));
    }

    @Test
    void criar_outraRequisicaoIgualGravouPrimeiro_devolveAReservaDelaSemTirarAsTravas() {
        // As travas são da compra que a outra requisição gravou: não podem ser removidas.
        Reserva gravada = reservaGravada();
        for (int assento : ASSENTOS) {
            Ingresso ingresso = new Ingresso();
            ingresso.setIdAssento(assento);
            gravada.adicionarIngresso(ingresso);
        }
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.empty(), Optional.of(gravada));
        resultadoDaTrava(1L);
        executarTransacoes();
        when(reservaRepository.saveAndFlush(any(Reserva.class)))
                .thenThrow(new DataIntegrityViolationException("id duplicado"));

        ReservaService.Criacao criacao = criar();

        assertThat(criacao.reserva()).isSameAs(gravada);
        assertThat(criacao.nova()).isFalse();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void criar_mesmaChaveCorpoDiferenteEmParalelo_retorna422ELiberaSoAsTravasDosAssentosQueNaoSaoDaGravada() {
        // A outra requisição gravou a compra com os assentos 10 e 12; esta pediu 10 e 11 com a mesma chave.
        Reserva gravada = reservaGravada();
        for (int assento : List.of(10, 12)) {
            Ingresso ingresso = new Ingresso();
            ingresso.setIdAssento(assento);
            gravada.adicionarIngresso(ingresso);
        }
        gravada.setHashRequisicao("outro-corpo");
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.empty(), Optional.of(gravada));
        resultadoDaTrava(2L);
        executarTransacoes();
        when(reservaRepository.saveAndFlush(any(Reserva.class)))
                .thenThrow(new DataIntegrityViolationException("id duplicado"));

        assertThatThrownBy(this::criar)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("assento:trava:11")),
                eq(ID_COMPRA.toString()));
    }

    @Test
    void criar_violacaoQueNaoEDoId_liberaAsTravasERelancaOErro() {
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.empty());
        resultadoDaTrava(1L);
        executarTransacoes();
        when(reservaRepository.saveAndFlush(any(Reserva.class)))
                .thenThrow(new DataIntegrityViolationException("check"));

        assertThatThrownBy(this::criar).isInstanceOf(DataIntegrityViolationException.class);

        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("assento:trava:10", "assento:trava:11")), eq(ID_COMPRA.toString()));
    }

    @Test
    void criar_travaJaEraDaCompra_segueParaAGravacaoESemLiberarSeFalhar() {
        // Resultado 2: uma requisição igual pode estar gravando, ou sobrou uma trava de uma falha anterior.
        // Em vez de recusar, a requisição tenta gravar; se falhar, não remove travas que podem ser da outra.
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.empty());
        resultadoDaTrava(2L);
        executarTransacoes();
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(new IllegalStateException("banco"));

        assertThatThrownBy(this::criar).isInstanceOf(IllegalStateException.class);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void criar_travaQueSobrouDeUmaFalha_naoImpedeACompraDeSerGravada() {
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.empty());
        resultadoDaTrava(2L);
        executarTransacoes();
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(criar().nova()).isTrue();
    }

    // ------------------------------------------------------------------ pagamento e cancelamento

    @Test
    void pagar_orquestradorAlterouAReservaAoMesmoTempo_reavaliaERegistraOPedido() {
        // Ex.: assento.reservado chegou entre a leitura e a gravação e a compra foi para AGUARDANDO_PAGAMENTO.
        Reserva lida = reservaGravada();
        Reserva atualizada = reservaGravada();
        atualizada.setEstado(EstadoReserva.AGUARDANDO_PAGAMENTO);
        executarTransacoes();
        when(reservaRepository.saveAndFlush(lida)).thenThrow(new ObjectOptimisticLockingFailureException(
                Reserva.class, ID_COMPRA));
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.of(atualizada));
        when(reservaRepository.saveAndFlush(atualizada)).thenReturn(atualizada);

        Reserva resultado = service.solicitarPagamento(lida, "p-1", MetodoPagamento.PIX, SimulacaoPagamento.APROVAR);

        assertThat(resultado).isSameAs(atualizada);
        assertThat(resultado.getChavePagamento()).isEqualTo("p-1");
    }

    @Test
    void pagar_reservaAlteradaEmTodasAsTentativas_retorna409() {
        Reserva reserva = reservaGravada();
        executarTransacoes();
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(new ObjectOptimisticLockingFailureException(
                Reserva.class, ID_COMPRA));
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenAnswer(i -> Optional.of(reservaGravada()));

        assertThatThrownBy(() -> service.solicitarPagamento(reserva, "p-1", MetodoPagamento.PIX, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(reservaRepository, org.mockito.Mockito.times(3)).saveAndFlush(any(Reserva.class));
    }

    @Test
    void pagar_mesmoPedidoGravadoAoMesmoTempo_devolveAReservaAtual() {
        Reserva reserva = reservaGravada();
        Reserva atual = reservaGravada();
        atual.registrarPedidoPagamento("p-1", hashPix(), MetodoPagamento.PIX, SimulacaoPagamento.APROVAR,
                LocalDateTime.now());
        executarTransacoes();
        when(reservaRepository.saveAndFlush(reserva)).thenThrow(new ObjectOptimisticLockingFailureException(
                Reserva.class, ID_COMPRA));
        when(reservaRepository.findComIngressosById(ID_COMPRA)).thenReturn(Optional.of(atual));

        Reserva resultado = service.solicitarPagamento(reserva, "p-1", MetodoPagamento.PIX,
                SimulacaoPagamento.APROVAR);

        assertThat(resultado).isSameAs(atual);
    }

    @Test
    void cancelar_alteradaAoMesmoTempoPorOutraOperacao_retorna409() {
        Reserva reserva = reservaGravada();
        executarTransacoes();
        when(reservaRepository.saveAndFlush(reserva)).thenThrow(new ObjectOptimisticLockingFailureException(
                Reserva.class, ID_COMPRA));

        assertThatThrownBy(() -> service.cancelarReserva(reserva, "c-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static String hashPix() {
        // Mesmo resumo que o serviço calcula para PIX + APROVAR.
        return org.springframework.test.util.ReflectionTestUtils.invokeMethod(ReservaServiceImpl.class,
                "hashPagamento", MetodoPagamento.PIX, SimulacaoPagamento.APROVAR);
    }
}
