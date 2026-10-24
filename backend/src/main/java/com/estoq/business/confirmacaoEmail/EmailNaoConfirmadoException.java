package com.estoq.business.confirmacaoEmail;

import com.estoq.core.exceptions.BaseException;
import com.estoq.core.exceptions.Severity;
import org.springframework.http.HttpStatus;

public class EmailNaoConfirmadoException extends BaseException {
    public EmailNaoConfirmadoException() {
        super("Confirme seu e-mail", "Confirme seu e-mail pelo link enviado para entrar. Você pode pedir outro link.",
                HttpStatus.FORBIDDEN, Severity.WARNING, "EMAIL_NAO_CONFIRMADO");
    }
}
