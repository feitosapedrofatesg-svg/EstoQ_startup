package com.estoq.business.entradas;

import com.estoq.core.exceptions.FieldValidationException;

import org.springframework.stereotype.Component;

@Component
public class EntradaValidation {

    public void validar(EntradaRequestDTO d) {
        if (d.quantidade() == null || d.quantidade().signum() <= 0) {
            throw new FieldValidationException("quantidade", "Informe quantidade positiva.");
        }
        if (d.valorTotalPago() == null || d.valorTotalPago().signum() < 0) {
            throw new FieldValidationException("valorTotalPago", "Informe valor pago não negativo.");
        }
        if ((d.conteudoEmbalagem() == null) != (d.unidadeConteudo() == null))
            throw new FieldValidationException("conteudoEmbalagem", "Informe conteúdo e unidade da embalagem juntos.");
        if (d.conteudoEmbalagem() != null && (d.conteudoEmbalagem().signum() <= 0
                || d.conteudoEmbalagem().scale() > 6 || d.conteudoEmbalagem().precision() > 18
                || d.unidadeCompra() != com.estoq.business.produtos.UnidadeMedida.UN))
            throw new FieldValidationException("conteudoEmbalagem", "Informe conteúdo positivo com até seis casas e compra em UN.");
        var semCusto = Boolean.TRUE.equals(d.semCusto());
        if (d.valorTotalPago().signum() == 0 && !semCusto) {
            throw new FieldValidationException("valorTotalPago", "Para valor zero, informe que a entrada é sem custo.");
        }
        if (semCusto && d.valorTotalPago().signum() > 0) {
            throw new FieldValidationException("semCusto", "Entrada sem custo não pode ter valor pago.");
        }
    }
}