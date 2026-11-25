package br.engsistemasdistribuidos.pagamento.mapper;

import br.engsistemasdistribuidos.pagamento.dto.response.PagamentoResponse;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PagamentoMapper {

    @Mapping(source = "metodoPagamento", target = "metodo")
    PagamentoResponse toResponse(Pagamento pagamento);
}