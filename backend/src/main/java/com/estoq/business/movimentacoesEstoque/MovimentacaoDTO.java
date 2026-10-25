package com.estoq.business.movimentacoesEstoque;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.estoq.business.produtos.UnidadeMedida;

public record MovimentacaoDTO(Long id, TipoMovimentacao tipo, LocalDateTime dataHora, Long produtoId,
        String produtoNome, Long loteId, String loteCodigo, Long loteVersion, BigDecimal quantidadeLoteAtual,
        Long usuarioId, String usuarioNome, Long produtoAbertoId, BigDecimal quantidade, BigDecimal quantidadeAnterior,
        BigDecimal quantidadePosterior, BigDecimal precoUnitario, BigDecimal custoConsumo,
        BigDecimal valorPrejuizo, BigDecimal valorTotalPago, BigDecimal diferencaApurada,
        String motivo, String descricaoMotivo, String observacao, UnidadeMedida unidadeMedida,
        String loteFabricante, BigDecimal valorAjuste) {
}
