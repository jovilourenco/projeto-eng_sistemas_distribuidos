package br.engsistemasdistribuidos.eventoassento.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import br.engsistemasdistribuidos.eventoassento.dto.request.EventoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.EventoResponse;
import br.engsistemasdistribuidos.eventoassento.model.Evento;

@Mapper(componentModel = "spring")
public interface EventoMapper {

    EventoMapper INSTANCE = Mappers.getMapper(EventoMapper.class);

    EventoResponse toResponse(Evento evento); 

    List<EventoResponse> toResponse(List<Evento> eventos); 

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataCadastro", ignore = true)
    @Mapping(target = "dataAtualizacao", ignore = true)
    @Mapping(target = "idUsuarioCadastro", ignore = true)
    @Mapping(target = "idUsuarioAtualizacao", ignore = true)
    Evento toEntity(EventoRequest request);

    EventoRequest toRequest(Evento evento);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataCadastro", ignore = true)
    @Mapping(target = "dataAtualizacao", ignore = true)
    @Mapping(target = "idUsuarioCadastro", ignore = true)
    @Mapping(target = "idUsuarioAtualizacao", ignore = true)
    void updateEntity(EventoRequest request, @MappingTarget Evento entity);
}