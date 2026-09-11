package com.estoq.business.produtos;

import com.estoq.core.exceptions.FieldValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ConversorUnidade {

    private ConversorUnidade() {
    }

    public static BigDecimal converter(BigDecimal quantidade, UnidadeMedida origem, UnidadeMedida destino) {
        if (quantidade == null || origem == null || destino == null) {
            throw new FieldValidationException("unidadeCompra", "Informe quantidade e unidades.");
        }
        BigDecimal resultado;
        if (origem == destino) {
            resultado = quantidade;
        } else if (origem == UnidadeMedida.KG && destino == UnidadeMedida.G || origem == UnidadeMedida.L && destino == UnidadeMedida.ML) {
            resultado = quantidade.multiply(BigDecimal.valueOf(1000));
        } else if (origem == UnidadeMedida.G && destino == UnidadeMedida.KG || origem == UnidadeMedida.ML && destino == UnidadeMedida.L) {
            resultado = quantidade.divide(BigDecimal.valueOf(1000));
        } else {
            throw new FieldValidationException("unidadeCompra", "Conversão incompatível: " + origem + " para " + destino + ".");
        }
        try {
            return resultado.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new FieldValidationException("quantidade", "A quantidade convertida deve ser representável com até 3 casas decimais.");
        }
    }
}