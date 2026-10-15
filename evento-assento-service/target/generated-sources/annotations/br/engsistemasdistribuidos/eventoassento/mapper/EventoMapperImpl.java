package br.engsistemasdistribuidos.eventoassento.mapper;

import br.engsistemasdistribuidos.eventoassento.dto.request.EventoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.EventoResponse;
import br.engsistemasdistribuidos.eventoassento.model.Evento;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-15T19:01:22-0300",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.200.v20260930-0211, environment: Java 25.0.4.1 (Eclipse Adoptium)"
)
@Component
public class EventoMapperImpl implements EventoMapper {

    @Override
    public EventoResponse toResponse(Evento evento) {
        if ( evento == null ) {
            return null;
        }

        EventoResponse eventoResponse = new EventoResponse();

        eventoResponse.setId( evento.getId() );
        eventoResponse.setTitulo( evento.getTitulo() );
        eventoResponse.setDescricao( evento.getDescricao() );
        eventoResponse.setDataEvento( evento.getDataEvento() );
        eventoResponse.setDataCadastro( evento.getDataCadastro() );
        eventoResponse.setDataAtualizacao( evento.getDataAtualizacao() );
        eventoResponse.setIdUsuarioCadastro( evento.getIdUsuarioCadastro() );
        eventoResponse.setIdUsuarioAtualizacao( evento.getIdUsuarioAtualizacao() );

        return eventoResponse;
    }

    @Override
    public List<EventoResponse> toResponse(List<Evento> eventos) {
        if ( eventos == null ) {
            return null;
        }

        List<EventoResponse> list = new ArrayList<EventoResponse>( eventos.size() );
        for ( Evento evento : eventos ) {
            list.add( toResponse( evento ) );
        }

        return list;
    }

    @Override
    public Evento toEntity(EventoRequest request) {
        if ( request == null ) {
            return null;
        }

        Evento evento = new Evento();

        evento.setTitulo( request.getTitulo() );
        evento.setDescricao( request.getDescricao() );
        evento.setDataEvento( request.getDataEvento() );

        return evento;
    }

    @Override
    public EventoRequest toRequest(Evento evento) {
        if ( evento == null ) {
            return null;
        }

        EventoRequest eventoRequest = new EventoRequest();

        eventoRequest.setTitulo( evento.getTitulo() );
        eventoRequest.setDescricao( evento.getDescricao() );
        eventoRequest.setDataEvento( evento.getDataEvento() );

        return eventoRequest;
    }

    @Override
    public void updateEntity(EventoRequest request, Evento entity) {
        if ( request == null ) {
            return;
        }

        entity.setTitulo( request.getTitulo() );
        entity.setDescricao( request.getDescricao() );
        entity.setDataEvento( request.getDataEvento() );
    }
}
