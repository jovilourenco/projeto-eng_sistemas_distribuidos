package br.engsistemasdistribuidos.reserva.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    // Rede de segurança: duas alterações simultâneas no mesmo ingresso que escapem do tratamento
    // no ReservaServiceImpl. A perdedora recebe 409 em vez de 500 (ADR-002, item 2).
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail conflitoDeConcorrencia(ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O ingresso foi alterado por outra operação. Consulte-o novamente.");
    }
}
