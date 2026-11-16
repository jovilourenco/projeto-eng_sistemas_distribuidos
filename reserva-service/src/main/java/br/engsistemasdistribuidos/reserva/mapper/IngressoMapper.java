package br.engsistemasdistribuidos.reserva.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import br.engsistemasdistribuidos.reserva.dto.response.IngressoResponse;
import br.engsistemasdistribuidos.reserva.model.Ingresso;

@Mapper(componentModel = "spring")
public interface IngressoMapper {

    IngressoResponse toResponse(Ingresso ingresso);

    List<IngressoResponse> toResponse(List<Ingresso> ingressos);
}
