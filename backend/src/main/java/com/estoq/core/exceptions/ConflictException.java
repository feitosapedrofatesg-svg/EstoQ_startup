package com.estoq.core.exceptions;

import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException {

    public static final String MENSAGEM = "O estoque foi alterado por outro usuário. Atualize os dados e tente novamente.";

    public ConflictException() {
        this(MENSAGEM);
    }

    public ConflictException(String mensagem) {
        super("Conflito", mensagem, HttpStatus.CONFLICT, Severity.WARNING, "CONFLICT");
    }
}