package br.engsistemasdistribuidos.eventoassento.service.impl;

import static br.engsistemasdistribuidos.eventoassento.mensageria.MensagensAssento.*;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.engsistemasdistribuidos.eventoassento.mensageria.ComandoAssento;
import br.engsistemasdistribuidos.eventoassento.mensageria.RespostaAssento;
import br.engsistemasdistribuidos.eventoassento.model.Assento;
import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;
import br.engsistemasdistribuidos.eventoassento.repository.AssentoRepository;
import br.engsistemasdistribuidos.eventoassento.service.AssentoSagaService;

@Service
@Transactional
public class AssentoSagaServiceImpl implements AssentoSagaService {

    private static final Logger log = LoggerFactory.getLogger(AssentoSagaServiceImpl.class);

    private final AssentoRepository repository;

    public AssentoSagaServiceImpl(AssentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public RespostaAssento reservar(ComandoAssento comando) {
        int alterados = repository.reservar(comando.idAssento(), comando.idEvento(), comando.idCompra());
        if (alterados == 0) {
            // Outra compra detém o assento, ou ele está inativo, não existe ou é de outro evento.
            return RespostaAssento.para(comando, INDISPONIVEL);
        }
        // O valor informado na reserva é o que será cobrado, mesmo que o gestor altere o preço depois.
        Assento assento = repository.findById(comando.idAssento()).orElseThrow();
        return RespostaAssento.para(comando, RESERVADO, assento.getValor());
    }

    @Override
    public RespostaAssento vender(ComandoAssento comando) {
        int alterados = repository.vender(comando.idAssento(), comando.idCompra());
        if (alterados == 0) {
            // Só acontece se as garantias da SAGA forem violadas: a compra não detém o assento.
            log.error("Venda recusada: compra {} não detém o assento {}", comando.idCompra(), comando.idAssento());
            return RespostaAssento.para(comando, VENDA_RECUSADA);
        }
        return RespostaAssento.para(comando, VENDIDO);
    }

    @Override
    public RespostaAssento liberar(ComandoAssento comando) {
        int alterados = repository.liberar(comando.idAssento(), comando.idCompra());
        if (alterados == 1) {
            return RespostaAssento.para(comando, LIBERADO);
        }
        // 0 linhas: lê o assento para escolher a resposta (ADR-003, item 3).
        Optional<Assento> assento = repository.findById(comando.idAssento());
        boolean vendidoParaEstaCompra = assento
                .filter(a -> a.getStatus() == StatusAssento.VENDIDO)
                .filter(a -> comando.idCompra().equals(a.getIdCompra()))
                .isPresent();
        if (vendidoParaEstaCompra) {
            log.error("Liberação recusada: assento {} já foi vendido para a compra {}",
                    comando.idAssento(), comando.idCompra());
            return RespostaAssento.para(comando, LIBERACAO_RECUSADA);
        }
        // A compra já não detém o assento: não há o que liberar.
        return RespostaAssento.para(comando, LIBERADO);
    }
}
