package br.engsistemasdistribuidos.pagamento.mapper;

import br.engsistemasdistribuidos.pagamento.dto.response.PagamentoResponse;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PagamentoMapper {

    PagamentoResponse toResponse(Pagamento pagamento);
}