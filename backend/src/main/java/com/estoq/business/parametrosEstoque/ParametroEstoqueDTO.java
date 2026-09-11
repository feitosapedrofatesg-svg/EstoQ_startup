package com.estoq.business.parametrosEstoque;

import com.estoq.core.dtos.BaseDTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ParametroEstoqueDTO extends BaseDTO {

    @NotNull
    private Long produtoId;

    @NotNull
    @Min(0)
    private Integer tempoReposicaoDias;

    @NotNull
    @Min(1)
    private Integer periodoAnaliseDias;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 15, fraction = 3)
    private BigDecimal consumoMedioDiario;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 15, fraction = 3)
    private BigDecimal estoqueMinimo;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 15, fraction = 3)
    private BigDecimal estoqueMedio;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 15, fraction = 3)
    private BigDecimal estoqueMaximo;

    @NotNull
    @Min(0)
    private Integer diasAlertaVencimento;

    private LocalDateTime dataAtualizacao;
}