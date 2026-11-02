package com.estoq.business.produtos;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** O volume da garrafa não é a unidade usada para vender ou consumir a bebida. */
public final class EstoqueBebida {
    private static final Pattern BEBIDA = Pattern.compile(
            "^(BEB|BEBIDA|REFRI|REFRIG|REFRIGERANTE|SUCO|CERVEJA|VINHO|ENERGETICO|"
            + "AGUA|H2O|H2OH|COCA COLA|FANTA|SPRITE|PEPSI|GUARANA)( |$)");

    private EstoqueBebida() { }

    public static boolean porUnidade(String nome, String categoria) {
        String produto = normalizar(nome);
        return normalizar(categoria).matches(".*\\bBEBIDAS?\\b.*")
                || (!produto.startsWith("AGUA SANITARIA") && BEBIDA.matcher(produto).find());
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", " ").replaceAll("\\s+", " ").strip();
    }
}
