package com.estoq.business.relatorios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class FormatoRelatorioPdf {
    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    private FormatoRelatorioPdf() {}

    static String data(LocalDate valor) { return valor == null ? "-" : valor.format(DATA); }

    static String qtd(BigDecimal valor, String unidade) {
        return numero(valor) + (unidade == null || unidade.isBlank() ? "" : " " + unidade.toLowerCase(BRASIL));
    }

    static String numero(BigDecimal valor) {
        if (valor == null) return "-";
        var formato = NumberFormat.getNumberInstance(BRASIL);
        formato.setMaximumFractionDigits(Math.max(3, valor.scale()));
        return formato.format(valor);
    }

    /** Os totais em dinheiro somam os mesmos centavos mostrados em cada linha. */
    static BigDecimal valorMonetario(BigDecimal valor) {
        return com.estoq.core.helpers.NumeroUtil.money(valor);
    }

    static String moeda(BigDecimal valor) {
        var formato = NumberFormat.getCurrencyInstance(BRASIL);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor == null ? BigDecimal.ZERO : valor);
    }

    static String percentual(BigDecimal valor) { return valor == null ? "Indisponível" : numero(valor) + "%"; }
}
