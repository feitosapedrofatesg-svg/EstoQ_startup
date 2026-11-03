package com.estoq.business.produtos;

import com.estoq.core.exceptions.FieldValidationException;
import java.math.BigDecimal;

public final class ConversorEmbalagem {
    private ConversorEmbalagem() { }
    public static BigDecimal calcular(BigDecimal quantidade, BigDecimal conteudo,
            UnidadeMedida origem, UnidadeMedida destino) {
        if (quantidade == null || quantidade.signum() <= 0 || conteudo == null || conteudo.signum() <= 0)
            throw new FieldValidationException("conteudoEmbalagem", "Informe quantidade e conteúdo positivos da embalagem.");
        var resultado = ConversorUnidade.converter(quantidade.multiply(conteudo), origem, destino);
        if (resultado.precision() > 15)
            throw new FieldValidationException("quantidade", "Quantidade acima do limite do estoque.");
        return resultado;
    }
}
