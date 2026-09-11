package com.estoq.business.movimentacoesEstoque;

import java.math.BigDecimal;
import java.util.List;

public record MovimentacaoResultadoDTO(Long produtoId, BigDecimal saldoAtual, List<MovimentacaoDTO> movimentacoes) {
}