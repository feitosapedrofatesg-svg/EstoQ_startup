package com.estoq.business.parametrosEstoque;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;

import org.springframework.stereotype.Component;

@Component
public class ParametroEstoqueValidation extends GenericValidation<ParametroEstoqueModel> implements IParametroEstoqueValidation {

    @Override
    public void validate(ParametroEstoqueModel p) {
        obrigatorio(p.getProduto(), "produtoId");
        naoNegativo(p.getConsumoMedioDiario(), "consumoMedioDiario");
        naoNegativo(p.getEstoqueMinimo(), "estoqueMinimo");
        naoNegativo(p.getEstoqueMedio(), "estoqueMedio");
        naoNegativo(p.getEstoqueMaximo(), "estoqueMaximo");
        if (p.getEstoqueMinimo().compareTo(p.getEstoqueMedio()) > 0 || p.getEstoqueMedio().compareTo(p.getEstoqueMaximo()) > 0) {
            throw new FieldValidationException("estoqueMinimo", "Respeite estoqueMinimo <= estoqueMedio <= estoqueMaximo.");
        }
        if (p.getPeriodoAnaliseDias() == null || p.getPeriodoAnaliseDias() <= 0 || p.getTempoReposicaoDias() == null
                || p.getTempoReposicaoDias() < 0 || p.getDiasAlertaVencimento() == null || p.getDiasAlertaVencimento() < 0) {
            throw new FieldValidationException("periodos", "Período de análise deve ser positivo; reposição e antecedência não podem ser negativos.");
        }
    }
}