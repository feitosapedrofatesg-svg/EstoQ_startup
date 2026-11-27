package com.estoq.business.balancos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemBalancoDTO(Long id, Long version, Long produtoId, String produtoNome, String unidadeMedida,
        Long categoriaId, String categoriaNome,
        BigDecimal quantidadeSistema, BigDecimal quantidadeFisica, BigDecimal diferenca, boolean ajusteAplicado,
        Long usuarioContagemId, String usuarioContagemNome, LocalDateTime contagemEm) {
}
