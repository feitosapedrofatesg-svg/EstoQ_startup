package com.estoq.business.relatorios;

import java.util.List;

/** Dados de apresentação; não calcula saldos, custos ou indicadores. */
record DocumentoRelatorioPdf(String titulo, String periodo, List<Coluna> colunas,
        List<List<String>> linhas, List<Destaque> resumo, String observacao, List<Destaque> totais) {
    DocumentoRelatorioPdf(String titulo, String periodo, List<Coluna> colunas,
            List<List<String>> linhas, List<Destaque> resumo, String observacao) {
        this(titulo, periodo, colunas, linhas, resumo, observacao, List.of());
    }
    record Coluna(String titulo, float peso, boolean numerica) {}
    record Destaque(String rotulo, String valor) {}
}
