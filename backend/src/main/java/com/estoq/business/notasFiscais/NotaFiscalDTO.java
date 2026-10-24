package com.estoq.business.notasFiscais;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.estoq.business.produtos.UnidadeMedida;

public record NotaFiscalDTO(Long id, Long version, String chave, String fornecedor, String documentoFornecedor,
        String numero, String serie, LocalDate emissao, BigDecimal valorTotal, boolean confirmada,
        List<String> avisos, List<Item> itens) {
    public record Item(Long id, int numero, String codigo, String descricao, String unidadeNota,
            BigDecimal quantidadeNota, BigDecimal valorBruto, BigDecimal desconto, BigDecimal valorRateado,
            BigDecimal valorEntrada, Long produtoId, Long categoriaId, String nomeProduto,
            UnidadeMedida unidadeEstoque, BigDecimal fator, BigDecimal quantidadeEntrada, LocalDate validade,
            boolean selecionado, boolean embalagemConfirmada, boolean incerto, Long movimentacaoId,
            String origemCategoria, String motivoCategoria, boolean revisarCategoria) { }
}
