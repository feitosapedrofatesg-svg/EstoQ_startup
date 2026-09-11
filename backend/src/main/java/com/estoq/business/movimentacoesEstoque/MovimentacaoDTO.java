package com.estoq.business.movimentacoesEstoque;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentacaoDTO(Long id, TipoMovimentacao tipo, LocalDateTime dataHora, Long produtoId,
        String produtoNome, Long loteId, String loteCodigo, Long loteVersion, BigDecimal quantidadeLoteAtual,
        Long usuarioId, Long produtoAbertoId, BigDecimal quantidade, BigDecimal quantidadeAnterior,
        BigDecimal quantidadePosterior, BigDecimal precoUnitario, BigDecimal custoConsumo,
        BigDecimal valorPrejuizo, BigDecimal valorTotalPago, BigDecimal diferencaApurada,
        String motivo, String descricaoMotivo, String observacao) {
}