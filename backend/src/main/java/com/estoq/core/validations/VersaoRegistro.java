package com.estoq.core.validations;

import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;
import java.util.Objects;

public final class VersaoRegistro {
    private VersaoRegistro() { }

    public static void conferir(Long enviada, Long atual) {
        conferir(enviada, atual, null);
    }

    public static void conferir(Long enviada, Long atual, String mensagem) {
        if (!Objects.equals(enviada, atual)) {
            if (mensagem == null) throw new ConflictException();
            throw new ConflictException(mensagem);
        }
    }

    public static void obrigatoria(Long enviada, Long atual) {
        if (enviada == null) throw new FieldValidationException("version", "Informe a versão consultada para atualizar.");
        conferir(enviada, atual);
    }

    public static void seInformada(Long enviada, Long atual) {
        if (enviada != null) conferir(enviada, atual);
    }
}
