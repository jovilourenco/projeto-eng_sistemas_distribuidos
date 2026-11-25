package br.engsistemasdistribuidos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.engsistemasdistribuidos.pagamento.dto.request.PagamentoRequest;
import br.engsistemasdistribuidos.pagamento.dto.response.PagamentoResponse;
import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.SimulacaoPagamento;
import br.engsistemasdistribuidos.pagamento.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.pagamento.repository.PagamentoRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PagamentoIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ticketflow_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("pagamento.simulacao.atraso-ms", () -> "200");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void limparBanco() {
        outboxEventRepository.deleteAll();
        pagamentoRepository.deleteAll();
    }

    @Test
    void dezRequisicoesParalelasComMesmaChaveDevemGerarUmaUnicaLinhaERespostasIguais() throws Exception {
        var idCompra = UUID.randomUUID();
        var request = new PagamentoRequest(
                idCompra, 1, new BigDecimal("250.00"), MetodoPagamento.PIX, SimulacaoPagamento.APROVAR
        );

        int totalRequisicoes = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalRequisicoes);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<ResponseEntity<PagamentoResponse>>> futuros = new ArrayList<>();

        for (int i = 0; i < totalRequisicoes; i++) {
            futuros.add(executor.submit(() -> {
                largada.await();
                return restTemplate.postForEntity("/pagamentos", request, PagamentoResponse.class);
            }));
        }

        largada.countDown();

        List<ResponseEntity<PagamentoResponse>> respostas = new ArrayList<>();
        for (Future<ResponseEntity<PagamentoResponse>> futuro : futuros) {
            respostas.add(futuro.get());
        }
        executor.shutdown();

        assertThat(respostas).hasSize(totalRequisicoes);
        assertThat(respostas).allSatisfy(r -> {
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).isNotNull();
            assertThat(r.getBody().status()).isEqualTo("APROVADO");
            assertThat(r.getBody().idCompra()).isEqualTo(idCompra);
        });

        Set<UUID> idsRetornados = respostas.stream()
                .map(r -> r.getBody().id())
                .collect(Collectors.toSet());

        assertThat(idsRetornados).hasSize(1);
        assertThat(pagamentoRepository.count()).isEqualTo(1L);
        assertThat(outboxEventRepository.count()).isEqualTo(1L);
    }

    @Test
    void pagamentoRecusadoDeveRetornar402() {
        var idCompra = UUID.randomUUID();
        var request = new PagamentoRequest(
                idCompra, 1, new BigDecimal("180.00"), MetodoPagamento.CARTAO_CREDITO, SimulacaoPagamento.RECUSAR
        );

        var response = restTemplate.postForEntity("/pagamentos", request, PagamentoResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("RECUSADO");
        assertThat(pagamentoRepository.count()).isEqualTo(1L);
    }

    @Test
    void pagamentoAtrasadoDeveAguardarEAprovarCom200() {
        var idCompra = UUID.randomUUID();
        var request = new PagamentoRequest(
                idCompra, 1, new BigDecimal("90.00"), MetodoPagamento.BOLETO, SimulacaoPagamento.ATRASAR
        );

        long inicio = System.currentTimeMillis();
        var response = restTemplate.postForEntity("/pagamentos", request, PagamentoResponse.class);
        long duracao = System.currentTimeMillis() - inicio;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("APROVADO");
        assertThat(duracao).isGreaterThanOrEqualTo(180L);
    }
}