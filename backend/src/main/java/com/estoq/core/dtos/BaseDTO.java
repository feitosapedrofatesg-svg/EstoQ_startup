package com.estoq.core.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class BaseDTO {

    private Long id;
    private Long version;
    private boolean ativo;
    private LocalDateTime dataHoraCriacao;
}