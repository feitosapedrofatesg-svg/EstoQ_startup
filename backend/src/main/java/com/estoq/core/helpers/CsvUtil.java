package com.estoq.core.helpers;

import java.util.List;
import java.util.stream.Collectors;

public final class CsvUtil {
    private CsvUtil() { }

    public static String linha(List<String> valores) {
        return valores.stream().map(CsvUtil::campo).collect(Collectors.joining(";")) + "\r\n";
    }

    private static String campo(String valor) {
        String texto = valor == null ? "" : valor;
        // Aspas protegem as colunas; o apóstrofo impede fórmulas em campos de texto.
        String inicio = texto.stripLeading();
        if (!inicio.isEmpty() && "=+@-".indexOf(inicio.charAt(0)) >= 0
                && !inicio.matches("[-+]?\\d+(?:[.,]\\d+)*%?")) texto = "'" + texto;
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }
}
