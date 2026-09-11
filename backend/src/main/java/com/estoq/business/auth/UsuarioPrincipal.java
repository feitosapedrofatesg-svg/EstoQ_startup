package com.estoq.business.auth;

import com.estoq.business.usuarios.UsuarioModel;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Somente dados de autenticação na sessão, nunca uma entidade JPA. */
public record UsuarioPrincipal(Long id, String email, String senha, String perfil) implements UserDetails {

    public static UsuarioPrincipal of(UsuarioModel u) {
        return new UsuarioPrincipal(u.getId(), u.getEmail(), u.getSenha(), u.getPerfil().name());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil));
    }
}