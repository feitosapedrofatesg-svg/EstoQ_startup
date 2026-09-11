package com.estoq.business.consumos;

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
@Table(name = "consumos")
@DiscriminatorValue("CONSUMO")
public class ConsumoModel extends MovimentacaoEstoqueModel {

    public ConsumoModel() {
        setTipo(TipoMovimentacao.CONSUMO);
    }

    @Transient
    public BigDecimal getCustoConsumo() {
        return NumeroUtil.money(getQuantidade().multiply(getLote().getPrecoUnitario()));
    }
}