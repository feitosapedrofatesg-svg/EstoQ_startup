package com.estoq.business.produtosAbertos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProdutoAbertoDTO(Long id, Long version, Long produtoId, String produtoNome, String unidadeMedida,
        Long loteId, String loteCodigo, LocalDate dataValidade, Long usuarioId, LocalDateTime dataAbertura,
        BigDecimal quantidadeAberta, BigDecimal quantidadeUtilizada, BigDecimal quantidadeRestante,
        boolean finalizado, BigDecimal quantidadeLoteAtual, BigDecimal saldoAtual,
        String loteFabricante) {
    public ProdutoAbertoDTO(Long id, Long version, Long produtoId, String produtoNome, String unidadeMedida, Long
            loteId, String loteCodigo, LocalDate dataValidade, Long usuarioId, LocalDateTime dataAbertura,
            BigDecimal quantidadeAberta, BigDecimal quantidadeUtilizada, BigDecimal quantidadeRestante, boolean
            finalizado, BigDecimal quantidadeLoteAtual, BigDecimal saldoAtual) {
        this(id, version, produtoId, produtoNome, unidadeMedida, loteId, loteCodigo, dataValidade, usuarioId,
                dataAbertura, quantidadeAberta, quantidadeUtilizada, quantidadeRestante, finalizado,
                quantidadeLoteAtual, saldoAtual, null);
    }

}
