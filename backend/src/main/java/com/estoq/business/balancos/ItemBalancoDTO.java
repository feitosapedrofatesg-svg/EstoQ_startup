package com.estoq.business.balancos;

import java.math.BigDecimal;

public record ItemBalancoDTO(Long id, Long produtoId, String produtoNome, String unidadeMedida,
        BigDecimal quantidadeSistema, BigDecimal quantidadeFisica, BigDecimal diferenca, boolean ajusteAplicado) {
}