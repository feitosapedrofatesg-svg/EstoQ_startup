package com.estoq.business.auth;

import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;

public record AuthenticatedUserDTO(Long id, String nome, String email, Perfil perfil) {

    public static AuthenticatedUserDTO of(UsuarioModel u) {
        return new AuthenticatedUserDTO(u.getId(), u.getNome(), u.getEmail(), u.getPerfil());
    }
}