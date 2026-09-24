package com.estoq.core.exceptions;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> negocio(BaseException ex) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ErrorResponse.error(ex));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> concorrencia(Exception ex) {
        return negocio(new ConflictException());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridade(Exception ex) {
        return negocio(new ConflictException("Registro duplicado ou com vínculos que impedem a operação."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> campos(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Dados inválidos.");
        return ResponseEntity.badRequest().body(ErrorResponse.error(mensagem));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> formato(Exception ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.error("Dados inválidos. Confira campos, unidades, datas e identificadores."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> inexistente(Exception ex) {
        return ResponseEntity.status(404).body(ErrorResponse.error("Recurso não encontrado."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception ex) {
        log.error("Falha inesperada ao processar requisição", ex);
        // DIAGNÓSTICO TEMPORÁRIO (21h 2026-09-24): expor a exceção real em produção.
        return ResponseEntity.internalServerError().body(ErrorResponse.error(
                "Ocorreu um erro inesperado ao processar a operação. [DIAG] "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage()));
    }
}