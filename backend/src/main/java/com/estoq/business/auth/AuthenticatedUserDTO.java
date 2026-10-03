package com.estoq.business.auth;

import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;

/**
 * Sessão do usuário. {@code restauranteNome} identifica a loja em que a tela está
 * operando — sem ele ninguém sabe qual restaurante está logado. É nulo para o
 * perfil PLATAFORMA, que não pertence a nenhuma loja.
 */
public record AuthenticatedUserDTO(Long id, String nome, String email, Perfil perfil, String restauranteNome) {

    public static AuthenticatedUserDTO of(UsuarioModel u, String restauranteNome) {
        return new AuthenticatedUserDTO(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), restauranteNome);
    }
}
