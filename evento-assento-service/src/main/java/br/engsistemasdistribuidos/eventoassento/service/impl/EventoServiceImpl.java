package br.engsistemasdistribuidos.eventoassento.service.impl;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.engsistemasdistribuidos.eventoassento.dto.request.EventoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.EventoResponse;
import br.engsistemasdistribuidos.eventoassento.mapper.EventoMapper;
import br.engsistemasdistribuidos.eventoassento.model.Evento;
import br.engsistemasdistribuidos.eventoassento.model.TipoEvento;
import br.engsistemasdistribuidos.eventoassento.repository.EventoRepository;
import br.engsistemasdistribuidos.eventoassento.repository.TipoEventoRepository;
import br.engsistemasdistribuidos.eventoassento.service.EventoService;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class EventoServiceImpl implements EventoService {

    private final EventoRepository repository;
    private final EventoMapper mapper;
    private final TipoEventoRepository tipoEventoRepository;

    public EventoServiceImpl(EventoRepository repository, 
                        EventoMapper mapper, 
                        TipoEventoRepository tipoEventoRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.tipoEventoRepository = tipoEventoRepository;   
    }

    @Override
    public EventoResponse criar(EventoRequest request) {
        Evento evento = mapper.toEntity(request);
    
        carregarEntidadesRelacionadas(evento);

        // TODO: Por enquanto, fazemos o mock do id do usuário.
        // Futuramente será por token JWT (se conseguirmos implementar)
        evento.setIdUsuarioCadastro(1); 
        
        evento = repository.save(evento);
        return mapper.toResponse(evento);
    }

    @Override
    public List<EventoResponse> listar(String titulo) {
        Specification<Evento> spec = EventoRepository.byTitulo(titulo);
        List<Evento> eventos = repository.findAll(spec);
        return mapper.toResponse(eventos);
    }

    @Override
    public EventoResponse buscarPorId(int idEvento) {
        Evento evento = recuperarEvento(idEvento);
        return mapper.toResponse(evento);
    }

    @Override
    public EventoResponse atualizar(int idEvento, EventoRequest request) {
        Evento evento = recuperarEvento(idEvento);
        
        mapper.updateEntity(request, evento);
        
        carregarEntidadesRelacionadas(evento);
        
        // TODO: Por enquanto, fazemos o mock do id do usuário.
        // Futuramente será por token JWT (se conseguirmos implementar)
        evento.setIdUsuarioAtualizacao(1);
        
        evento = repository.save(evento);
        return mapper.toResponse(evento);
    }

    @Override
    public void excluir(int idEvento) {
        Evento evento = recuperarEvento(idEvento);
        repository.delete(evento);
    }

    private Evento recuperarEvento(int idEvento) {
        return repository.findOne(EventoRepository.byId(idEvento))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
    }

    private void carregarEntidadesRelacionadas(Evento evento) {
        
        if (evento.getTipoEvento() == null || evento.getTipoEvento().getId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O Tipo de Evento é obrigatório.");
        }

        TipoEvento tipo = tipoEventoRepository.findById(evento.getTipoEvento().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "O Tipo de Evento informado é inválido ou não existe."));
        
        evento.setTipoEvento(tipo);
    }
}