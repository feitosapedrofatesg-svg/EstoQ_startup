package com.estoq.business.produtos;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class EstoqueLimpeza {
    private static final Pattern TIPO = Pattern.compile("^(ESPONJA|DETERGENTE|DETERG|DESINFETANTE|DESINF|SABAO|SABONETE|ALVEJANTE|LIMPADOR|DESENGORDURANTE|AMACIANTE|AGUA SANITARIA|ALCOOL (?:LIQ|LIQUIDO|GEL)|SACO (?:DE |PARA )?LIXO|PALHA DE ACO|LA DE ACO|CREME DENTAL|PAPEL HIGIENICO)( |$)");
    private static final Pattern CONTAGEM = Pattern.compile("(?i)(?:\\bC\\s*/\\s*|\\bCOM\\s+)(\\d+)\\b|\\b(\\d+)\\s*(?:UN|UND|UNID|UNIDADES)\\b");
    private EstoqueLimpeza() { }
    public static boolean porUnidade(String nome, String categoria) {
        return normalizar(categoria).matches(".*\\b(?:LIMPEZA|HIGIENE)\\b.*") || TIPO.matcher(normalizar(nome)).find();
    }
    public static BigDecimal unidadesPorEmbalagem(String descricao) {
        var matcher = CONTAGEM.matcher(descricao == null ? "" : descricao);
        BigDecimal quantidade = null;
        while (matcher.find()) {
            var valor = new BigDecimal(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
            if (valor.signum() <= 0 || quantidade != null && quantidade.compareTo(valor) != 0) return null;
            quantidade = valor;
        }
        return quantidade == null ? BigDecimal.ONE : quantidade;
    }
    public static boolean embalagemAvulsa(String unidade) {
        return normalizar(unidade).matches("UN|UN1|UND|UNID|UNIDADE|UNIDADES|PC|PC1|PCA|FR|FR1|FRASCO|FRASCO1");
    }
    private static String normalizar(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", " ").replaceAll("\\s+", " ").strip();
    }
}
