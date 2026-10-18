package com.estoq.business.parametrosCmv;

import jakarta.validation.constraints.Digits;

import jakarta.validation.constraints.DecimalMax;

import jakarta.validation.constraints.DecimalMin;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ParametroCmvDTO(Long id, Long version, @NotNull
        @DecimalMin("0")
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 2) BigDecimal percentualIdeal) {
}