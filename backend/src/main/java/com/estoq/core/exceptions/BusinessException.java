package com.estoq.core.exceptions;

import org.springframework.http.HttpStatus;

public class BusinessException extends BaseException {

    public BusinessException(String message, HttpStatus httpStatus) {
        super("Erro de Negócio", message, httpStatus, Severity.ERROR, "BUSINESS_ERROR");
    }
}