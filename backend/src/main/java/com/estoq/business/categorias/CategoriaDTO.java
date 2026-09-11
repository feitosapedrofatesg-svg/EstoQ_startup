package com.estoq.business.categorias;

import com.estoq.core.dtos.BaseDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CategoriaDTO extends BaseDTO {

    @NotBlank
    @Size(max = 120)
    private String nome;

    @Size(max = 500)
    private String descricao;
}