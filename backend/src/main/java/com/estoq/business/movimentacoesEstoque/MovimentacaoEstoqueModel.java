package com.estoq.business.movimentacoesEstoque;

import com.estoq.business.itensBalanco.ItemBalancoModel;
import com.estoq.business.lotes.LoteModel;
import com.estoq.business.produtos.ProdutoModel;
import com.estoq.business.produtosAbertos.ProdutoAbertoModel;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.domains.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "movimentacoes", indexes = {
        @Index(name = "idx_mov_tipo_data", columnList = "tipo,data_hora"),
        @Index(name = "idx_mov_lote", columnList = "lote_id"),
        @Index(name = "idx_mov_produto", columnList = "produto_id")})
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo", length = 20)
public abstract class MovimentacaoEstoqueModel extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", insertable = false, updatable = false)
    private TipoMovimentacao tipo;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProdutoModel produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoteModel lote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UsuarioModel usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_aberto_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProdutoAbertoModel produtoAberto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_balanco_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ItemBalancoModel itemBalanco;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal quantidade;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal quantidadeAnterior;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal quantidadePosterior;

    @Column(length = 500)
    private String observacao;

    public void registrar(LoteModel lote, UsuarioModel usuario, BigDecimal quantidade, BigDecimal anterior,
            BigDecimal posterior, String observacao, ProdutoAbertoModel aberto) {
        this.lote = lote;
        this.produto = lote.getProduto();
        this.usuario = usuario;
        this.quantidade = quantidade;
        this.quantidadeAnterior = anterior;
        this.quantidadePosterior = posterior;
        this.observacao = observacao;
        this.produtoAberto = aberto;
        this.dataHora = LocalDateTime.now();
    }

    @Transient
    public BigDecimal getDelta() {
        return quantidadePosterior.subtract(quantidadeAnterior);
    }
}