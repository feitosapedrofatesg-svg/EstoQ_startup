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
        var semCusto = Boolean.TRUE.equals(d.semCusto());
        if (d.valorTotalPago().signum() == 0 && !semCusto) {
            throw new FieldValidationException("valorTotalPago", "Para valor zero, informe que a entrada é sem custo.");
        }
        if (semCusto && d.valorTotalPago().signum() > 0) {
            throw new FieldValidationException("semCusto", "Entrada sem custo não pode ter valor pago.");
        }
    }
}