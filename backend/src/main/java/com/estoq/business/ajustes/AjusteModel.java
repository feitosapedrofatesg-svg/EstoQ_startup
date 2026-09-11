package com.estoq.business.ajustes;

import com.estoq.business.movimentacoesEstoque.MovimentacaoEstoqueModel;
import com.estoq.business.movimentacoesEstoque.TipoMovimentacao;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

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
}