package com.estoq.business.relatorios;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import static com.estoq.business.relatorios.DocumentoRelatorioPdf.Destaque;
import static com.estoq.business.relatorios.FormatoRelatorioPdf.numero;

/** Agrupa apenas grandezas compatíveis; kg e litros nunca formam um total único. */
final class TotaisRelatorioPdf {
    private final List<Destaque> valores = new ArrayList<>();
    private final TreeMap<String, BigDecimal> quantidades = new TreeMap<>();

    void adicionar(String rotulo, String valor) { valores.add(new Destaque(rotulo, valor)); }

    void quantidade(String rotulo, BigDecimal valor, String unidade) {
        // Sem unidade identificada, o valor permanece somente na linha do produto.
        if (valor == null || unidade == null || unidade.isBlank()) return;
        String chave = rotulo + " (" + unidade.trim().toLowerCase(Locale.ROOT) + ")";
        quantidades.merge(chave, valor, BigDecimal::add);
    }

    List<Destaque> listar() {
        var resultado = new ArrayList<>(valores);
        quantidades.forEach((rotulo, valor) -> resultado.add(new Destaque(rotulo, numero(valor))));
        return resultado;
    }
}
