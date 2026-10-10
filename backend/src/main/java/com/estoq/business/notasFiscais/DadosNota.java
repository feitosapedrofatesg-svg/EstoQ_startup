package com.estoq.business.notasFiscais;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resultado da leitura: ainda não representa uma entrada no estoque. */
public record DadosNota(String chave, String fornecedor, String documentoFornecedor, String numero,
        String serie, LocalDate emissao, BigDecimal valorTotal, List<ItemLido> itens, List<String> avisos) {
    public record ItemLido(int numero, String codigo, String descricao, String codigoBarras, String unidade,
            BigDecimal quantidade, BigDecimal valorBruto, BigDecimal desconto, LocalDate validade, boolean incerto) { }
}
