package com.estoq.business.usuarios;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(Long id, Long version, String nome, String email, Perfil perfil,
        boolean ativo, LocalDateTime dataHoraCriacao) {
}