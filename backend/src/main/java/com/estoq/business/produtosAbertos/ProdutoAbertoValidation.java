package com.estoq.business.produtosAbertos;

import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ProdutoAbertoValidation {

    public void validarAbertura(BigDecimal embalagem, BigDecimal usado, BigDecimal quantidadeFechada) {
        if (embalagem == null || embalagem.signum() <= 0 || embalagem.stripTrailingZeros().scale() > 3) {
            throw new FieldValidationException("quantidadeDaEmbalagem", "Informe uma embalagem positiva com até 3 casas decimais.");
        }
        if (usado == null || usado.signum() < 0 || usado.compareTo(embalagem) > 0 || usado.stripTrailingZeros().scale() > 3) {
            throw new FieldValidationException("quantoUsouAgora", "O utilizado deve estar entre zero e a quantidade da embalagem.");
        }
        if (embalagem.compareTo(quantidadeFechada) > 0) {
            throw new ConflictException("A embalagem excede o saldo ainda não vinculado a itens abertos deste lote.");
        }
    }
}