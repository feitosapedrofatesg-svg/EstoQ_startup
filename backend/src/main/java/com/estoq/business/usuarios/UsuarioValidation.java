package com.estoq.business.usuarios;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsuarioValidation extends GenericValidation<UsuarioModel> implements IUsuarioValidation {

    private final IUsuarioRepository repository;

    @Override
    public void validate(UsuarioModel u) {
        obrigatorio(u.getNome(), "nome");
        obrigatorio(u.getEmail(), "email");
        obrigatorio(u.getSenha(), "senha");
        obrigatorio(u.getPerfil(), "perfil");
        boolean duplicado = u.getId() == null ? repository.existsByEmailIgnoreCase(u.getEmail())
                : repository.existsByEmailIgnoreCaseAndIdNot(u.getEmail(), u.getId());
        if (duplicado) {
            throw new FieldValidationException("email", "Este e-mail já está cadastrado.");
        }
    }
}