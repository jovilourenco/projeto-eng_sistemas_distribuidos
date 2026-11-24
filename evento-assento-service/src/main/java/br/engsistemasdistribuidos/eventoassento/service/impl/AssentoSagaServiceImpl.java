package br.engsistemasdistribuidos.eventoassento.service.impl;

import static br.engsistemasdistribuidos.eventoassento.mensageria.MensagensAssento.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import br.engsistemasdistribuidos.eventoassento.mensageria.ComandoAssento;
import br.engsistemasdistribuidos.eventoassento.mensageria.RespostaAssento;
import br.engsistemasdistribuidos.eventoassento.mensageria.ValorAssento;
import br.engsistemasdistribuidos.eventoassento.model.Assento;
import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;
import br.engsistemasdistribuidos.eventoassento.repository.AssentoRepository;
import br.engsistemasdistribuidos.eventoassento.service.AssentoSagaService;

/**
 * Tudo ou nada: cada comando roda numa transação. Se um dos assentos não puder mudar de status,
 * a transação é marcada para desfazer as alterações dos outros, e a resposta de recusa é devolvida.
 *
 * Os assentos são alterados sempre em ordem crescente de id. Duas compras que disputam assentos em
 * comum travam as linhas na mesma ordem, então uma espera a outra em vez de entrar em deadlock.
 *
 * REQUIRES_NEW: cada comando é dono da própria transação. Se um dia o serviço for chamado dentro de outra
 * transação, a recusa desfaz só o comando, em vez de marcar a transação de fora e fazê-la falhar no commit.
 *
 * O comando já chega validado pelo listener (1 a 4 assentos distintos, não nulos, e evento no reservar).
 */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class AssentoSagaServiceImpl implements AssentoSagaService {

    private static final Logger log = LoggerFactory.getLogger(AssentoSagaServiceImpl.class);

    private final AssentoRepository repository;

    public AssentoSagaServiceImpl(AssentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public RespostaAssento reservar(ComandoAssento comando) {
        for (Integer idAssento : emOrdem(comando)) {
            if (repository.reservar(idAssento, comando.idEvento(), comando.idCompra()) == 0) {
                // Outra compra detém o assento, ou ele está inativo, não existe ou é de outro evento.
                desfazer();
                return RespostaAssento.para(comando, INDISPONIVEL);
            }
        }
        // O valor informado na reserva é o que será cobrado, mesmo que o gestor altere o preço depois.
        Map<Integer, Assento> assentos = repository.findAllById(comando.idAssentos()).stream()
                .collect(Collectors.toMap(Assento::getId, Function.identity()));
        List<ValorAssento> valores = comando.idAssentos().stream()
                .map(id -> new ValorAssento(id, assentos.get(id).getValor()))
                .toList();
        return RespostaAssento.para(comando, RESERVADO, valores);
    }

    @Override
    public RespostaAssento vender(ComandoAssento comando) {
        for (Integer idAssento : emOrdem(comando)) {
            if (repository.vender(idAssento, comando.idCompra()) == 0) {
                // Só acontece se as garantias da SAGA forem violadas: a compra não detém o assento.
                log.error("Venda recusada: compra {} não detém o assento {}", comando.idCompra(), idAssento);
                desfazer();
                return RespostaAssento.para(comando, VENDA_RECUSADA);
            }
        }
        return RespostaAssento.para(comando, VENDIDO);
    }

    @Override
    public RespostaAssento liberar(ComandoAssento comando) {
        for (Integer idAssento : emOrdem(comando)) {
            if (repository.liberar(idAssento, comando.idCompra()) == 0 && vendidoPara(idAssento, comando.idCompra())) {
                // 0 linhas com o assento vendido para esta compra: violação das garantias (ADR-003, item 3).
                log.error("Liberação recusada: assento {} já foi vendido para a compra {}",
                        idAssento, comando.idCompra());
                desfazer();
                return RespostaAssento.para(comando, LIBERACAO_RECUSADA);
            }
            // 0 linhas nos outros casos: a compra já não detém o assento, não há o que liberar.
        }
        return RespostaAssento.para(comando, LIBERADO);
    }

    private boolean vendidoPara(Integer idAssento, UUID idCompra) {
        return repository.findById(idAssento)
                .filter(a -> a.getStatus() == StatusAssento.VENDIDO)
                .filter(a -> idCompra.equals(a.getIdCompra()))
                .isPresent();
    }

    private static List<Integer> emOrdem(ComandoAssento comando) {
        return comando.idAssentos().stream().sorted(Comparator.naturalOrder()).toList();
    }

    // Desfaz as alterações já feitas neste comando sem lançar exceção: a recusa é uma resposta válida,
    // e uma exceção faria o listener tentar de novo e mandar o comando para a fila de mensagens mortas.
    private static void desfazer() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
}
