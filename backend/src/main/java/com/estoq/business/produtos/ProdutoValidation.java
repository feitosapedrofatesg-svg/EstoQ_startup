package com.estoq.business.produtos;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;

import org.springframework.stereotype.Component;

@Component
public class ProdutoValidation extends GenericValidation<ProdutoModel> implements IProdutoValidation {

    @Override
    public void validate(ProdutoModel p) {
        obrigatorio(p.getNome(), "nome");
        obrigatorio(p.getUnidadeMedida(), "unidadeMedida");
        obrigatorio(p.getCategoria(), "categoriaId");
        if (!p.getCategoria().isAtivo()) {
            throw new FieldValidationException("categoriaId", "A categoria deve estar ativa.");
        }
        if (EstoquePorUnidade.aplicar(p.getNome(), p.getCategoria().getNome())
                && p.getUnidadeMedida() != UnidadeMedida.UN) {
            throw new FieldValidationException("unidadeMedida",
                    "Bebidas e materiais de limpeza devem ser controlados em unidades (UN). Peso e volume podem constar no nome.");
        }
    }
}
