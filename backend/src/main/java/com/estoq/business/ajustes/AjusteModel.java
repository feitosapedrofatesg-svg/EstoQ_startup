package com.estoq.business.ajustes;

import com.estoq.business.movimentacoesEstoque.MovimentacaoEstoqueModel;
import com.estoq.business.movimentacoesEstoque.TipoMovimentacao;
import com.estoq.core.helpers.NumeroUtil;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ajustes")
@DiscriminatorValue("AJUSTE")
public class AjusteModel extends MovimentacaoEstoqueModel {

    public AjusteModel() {
        setTipo(TipoMovimentacao.AJUSTE);
    }

    @Transient
    public BigDecimal getValorAjuste() {
        return NumeroUtil.money(getDelta().abs().multiply(getLote().getPrecoUnitario()));
    }
}
