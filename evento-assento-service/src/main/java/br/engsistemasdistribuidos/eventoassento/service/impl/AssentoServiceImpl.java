package br.engsistemasdistribuidos.eventoassento.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.engsistemasdistribuidos.eventoassento.dto.request.AssentoLoteRequest;
import br.engsistemasdistribuidos.eventoassento.dto.request.AssentoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.AssentoResponse;
import br.engsistemasdistribuidos.eventoassento.mapper.AssentoMapper;
import br.engsistemasdistribuidos.eventoassento.model.Assento;
import br.engsistemasdistribuidos.eventoassento.model.Evento;
import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;
import br.engsistemasdistribuidos.eventoassento.repository.AssentoRepository;
import br.engsistemasdistribuidos.eventoassento.repository.EventoRepository;
import br.engsistemasdistribuidos.eventoassento.service.AssentoService;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class AssentoServiceImpl implements AssentoService {

    private static final int MAX_ASSENTOS_POR_LOTE = 1000;

    private final AssentoRepository repository;
    private final EventoRepository eventoRepository;
    private final AssentoMapper mapper;

    public AssentoServiceImpl(AssentoRepository repository,
                              EventoRepository eventoRepository,
                              AssentoMapper mapper) {
        this.repository = repository;
        this.eventoRepository = eventoRepository;
        this.mapper = mapper;
    }

    @Override
    public List<AssentoResponse> criarEmLote(int idEvento, AssentoLoteRequest request) {
        Evento evento = recuperarEvento(idEvento);
        validarLote(request);

        List<Assento> assentos = new ArrayList<>();
        for (int i = 0; i < request.getQuantidade(); i++) {
            Assento assento = new Assento();
            assento.setEvento(evento);
            assento.setTipoAssento(request.getTipoAssento().trim());
            assento.setValor(request.getValor());
            assento.setStatus(StatusAssento.DISPONIVEL);
            assento.setAtivo(true);
            assentos.add(assento);
        }

        return mapper.toResponse(repository.saveAll(assentos));
    }

    @Override
    public List<AssentoResponse> listar(int idEvento, StatusAssento status) {
        recuperarEvento(idEvento);

        Specification<Assento> spec = AssentoRepository.byEvento(idEvento)
                .and(AssentoRepository.byStatus(status));
        return mapper.toResponse(repository.findAll(spec, Sort.by("id")));
    }

    @Override
    public AssentoResponse buscarPorId(int idEvento, int idAssento) {
        return mapper.toResponse(recuperarAssento(idEvento, idAssento));
    }

    @Override
    public AssentoResponse atualizar(int idEvento, int idAssento, AssentoRequest request) {
        Assento assento = recuperarAssento(idEvento, idAssento);

        if (assento.getStatus() != StatusAssento.DISPONIVEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assentos reservados ou vendidos não podem ser alterados.");
        }
        validarAtualizacao(request);

        mapper.updateEntity(request, assento);
        return mapper.toResponse(repository.save(assento));
    }

    @Override
    public void excluir(int idEvento, int idAssento) {
        Assento assento = recuperarAssento(idEvento, idAssento);

        if (assento.getStatus() != StatusAssento.DISPONIVEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assentos reservados ou vendidos não podem ser excluídos.");
        }
        repository.delete(assento);
    }

    private Evento recuperarEvento(int idEvento) {
        return eventoRepository.findById(idEvento)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
    }

    // Busca pelo par (evento, assento): garante que o assento pertence ao evento da URL.
    private Assento recuperarAssento(int idEvento, int idAssento) {
        Specification<Assento> spec = AssentoRepository.byId(idAssento)
                .and(AssentoRepository.byEvento(idEvento));
        return repository.findOne(spec)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Assento não encontrado para este evento."));
    }

    private void validarLote(AssentoLoteRequest request) {
        if (request.getQuantidade() == null
                || request.getQuantidade() < 1
                || request.getQuantidade() > MAX_ASSENTOS_POR_LOTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A quantidade deve estar entre 1 e " + MAX_ASSENTOS_POR_LOTE + ".");
        }
        if (request.getTipoAssento() == null || request.getTipoAssento().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo do assento é obrigatório.");
        }
        if (request.getValor() == null || request.getValor().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O valor é obrigatório e deve ser maior ou igual a zero.");
        }
    }

    private void validarAtualizacao(AssentoRequest request) {
        if (request.getTipoAssento() != null && request.getTipoAssento().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo do assento não pode ser vazio.");
        }
        if (request.getValor() != null && request.getValor().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor deve ser maior ou igual a zero.");
        }
    }
}
