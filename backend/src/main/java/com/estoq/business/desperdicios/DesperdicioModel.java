package com.estoq.business.desperdicios;

import com.estoq.business.movimentacoesEstoque.MovimentacaoEstoqueModel;
import com.estoq.business.movimentacoesEstoque.TipoMovimentacao;
import com.estoq.core.helpers.NumeroUtil;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "desperdicios")
@DiscriminatorValue("DESPERDICIO")
public class DesperdicioModel extends MovimentacaoEstoqueModel {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MotivoDesperdicio motivo;

    @Column(length = 500)
    private String descricaoMotivo;

    public DesperdicioModel() {
        setTipo(TipoMovimentacao.DESPERDICIO);
    }

    @Transient
    public BigDecimal getValorPrejuizo() {
        return NumeroUtil.money(getQuantidade().multiply(getLote().getPrecoUnitario()));
    }
}