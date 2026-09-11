package com.estoq.business.entradas;

import com.estoq.business.movimentacoesEstoque.MovimentacaoEstoqueModel;
import com.estoq.business.movimentacoesEstoque.TipoMovimentacao;
import com.estoq.business.produtos.UnidadeMedida;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "entradas")
@DiscriminatorValue("ENTRADA")
public class EntradaModel extends MovimentacaoEstoqueModel {

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal valorTotalPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnidadeMedida unidadeCompra;

    private LocalDate dataValidade;

    public EntradaModel() {
        setTipo(TipoMovimentacao.ENTRADA);
    }
}