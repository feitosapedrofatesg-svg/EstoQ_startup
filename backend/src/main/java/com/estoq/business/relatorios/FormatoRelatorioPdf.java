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
    static DateTimeFormatter datas() {if(com.estoq.core.helpers.Idioma.atual().equals("pt-BR"))return DATA;return DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(com.estoq.core.helpers.Idioma.locale());}

    static String data(LocalDate valor) { return valor == null ? "-" : valor.format(datas()); }

    static String qtd(BigDecimal valor, String unidade) {
        return numero(valor) + (unidade == null || unidade.isBlank() ? "" : " " + unidade.toLowerCase(BRASIL));
    }

    static String numero(BigDecimal valor) {
        if (valor == null) return "-";
        var formato = NumberFormat.getNumberInstance(com.estoq.core.helpers.Idioma.locale());
        formato.setMaximumFractionDigits(Math.max(3, valor.scale()));
        return formato.format(valor);
    }

    /** Os totais em dinheiro somam os mesmos centavos mostrados em cada linha. */
    static BigDecimal valorMonetario(BigDecimal valor) {
        return com.estoq.core.helpers.NumeroUtil.money(valor);
    }

    static String moeda(BigDecimal valor) {
        var formato = NumberFormat.getCurrencyInstance(com.estoq.core.helpers.Idioma.locale());
        formato.setCurrency(java.util.Currency.getInstance("BRL"));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor == null ? BigDecimal.ZERO : valor);
    }

    static String percentual(BigDecimal valor) { return valor == null ? com.estoq.core.helpers.Idioma.texto("Indisponível") : numero(valor) + "%"; }
}
