package com.estoq.business.notasFiscais;

import com.estoq.core.domains.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "notas_fiscais", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurante_id", "chave"}))
public class NotaFiscalModel extends TenantEntity {
    @Column(nullable = false, length = 44) private String chave;
    @Column(nullable = false, length = 160) private String fornecedor;
    @Column(nullable = false, length = 20) private String documentoFornecedor;
    @Column(nullable = false, length = 20) private String numero;
    @Column(nullable = false, length = 10) private String serie;
    private LocalDate emissao;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal valorTotal;
    @Column(nullable = false) private boolean confirmada;
    private Long usuarioId;
    @Column(length = 2000) private String avisos;
    @OneToMany(mappedBy = "nota", cascade = CascadeType.ALL)
    @OrderBy("numero ASC")
    private List<ItemNotaFiscalModel> itens = new ArrayList<>();
}
