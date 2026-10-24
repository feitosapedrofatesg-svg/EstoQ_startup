package com.estoq.business.notasFiscais;

import com.estoq.core.domains.TenantEntity;
import com.estoq.business.produtos.UnidadeMedida;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "itens_nota_fiscal", uniqueConstraints = @UniqueConstraint(columnNames = {"nota_id", "numero"}))
public class ItemNotaFiscalModel extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "nota_id", nullable = false) private NotaFiscalModel nota;
    @Column(nullable = false) private int numero;
    @Column(nullable = false, length = 60) private String codigo;
    @Column(nullable = false, length = 300) private String descricao;
    @Column(length = 40) private String codigoBarras;
    @Column(nullable = false, length = 20) private String unidadeNota;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal quantidadeNota;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal valorBruto;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal desconto;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal valorRateado;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal valorEntrada;
    private Long produtoId;
    private Long categoriaId;
    @Column(length = 30) private String origemCategoria;
    @Column(length = 500) private String motivoCategoria;
    @Column(nullable = false) private boolean revisarCategoria;
    @Column(length = 120) private String nomeProduto;
    @Enumerated(EnumType.STRING) @Column(length = 10) private UnidadeMedida unidadeEstoque;
    @Column(precision = 18, scale = 6) private BigDecimal fator;
    @Column(precision = 18, scale = 3) private BigDecimal quantidadeEntrada;
    private LocalDate validade;
    @jakarta.persistence.Column(length = 50) private String loteFabricante;
    @Column(nullable = false) private boolean selecionado = true;
    @Column(nullable = false) private boolean embalagemConfirmada;
    @Column(nullable = false) private boolean incerto;
    private Long movimentacaoId;
}
