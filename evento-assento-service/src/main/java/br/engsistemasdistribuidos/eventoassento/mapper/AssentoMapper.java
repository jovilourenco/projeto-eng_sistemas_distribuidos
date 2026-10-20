package br.engsistemasdistribuidos.eventoassento.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import br.engsistemasdistribuidos.eventoassento.dto.request.AssentoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.AssentoResponse;
import br.engsistemasdistribuidos.eventoassento.model.Assento;

@Mapper(componentModel = "spring")
public interface AssentoMapper {

    @Mapping(source = "evento.id", target = "idEvento")
    AssentoResponse toResponse(Assento assento);

    List<AssentoResponse> toResponse(List<Assento> assentos);

    // Atualização parcial: campos null no request não sobrescrevem o assento.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "evento", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(AssentoRequest request, @MappingTarget Assento assento);
}
