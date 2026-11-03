package com.estoq.business.produtos;

public final class EstoquePorUnidade {
    private EstoquePorUnidade() { }
    public static boolean aplicar(String nome, String categoria) {
        return EstoqueBebida.porUnidade(nome, categoria) || EstoqueLimpeza.porUnidade(nome, categoria);
    }
}
