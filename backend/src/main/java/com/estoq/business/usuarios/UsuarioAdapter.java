package com.estoq.business.usuarios;

import org.springframework.stereotype.Component;

@Component
public class UsuarioAdapter {

    public UsuarioResponseDTO toDto(UsuarioModel u) {
        return new UsuarioResponseDTO(u.getId(), u.getVersion(), u.getNome(), u.getEmail(),
                u.getPerfil(), u.isAtivo(), u.getDataHoraCriacao());
    }
}