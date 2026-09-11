package com.estoq.business.produtos;

import com.estoq.core.dtos.BaseDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProdutoDTO extends BaseDTO {

    @NotBlank
    @Size(max = 120)
    private String nome;

    @NotNull
    private UnidadeMedida unidadeMedida;

    @NotNull
    private Long categoriaId;

    @Size(max = 40)
    private String codigoBarras;

    private String categoriaNome;
    private Long parametroEstoqueId;
    private BigDecimal saldoAtual;
}