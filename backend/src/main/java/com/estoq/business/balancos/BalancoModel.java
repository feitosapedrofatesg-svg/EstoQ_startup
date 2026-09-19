package com.estoq.business.balancos;

import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.itensBalanco.ItemBalancoModel;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.domains.BaseModel;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "balancos", indexes = {
        @Index(name = "idx_balanco_status", columnList = "status"),
        @Index(name = "idx_balanco_data", columnList = "data_hora")})
public class BalancoModel extends BaseModel {

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoBalanco tipo = TipoBalanco.GERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusBalanco status = StatusBalanco.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UsuarioModel usuario;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "balanco_categorias", joinColumns = @JoinColumn(name = "balanco_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_id"))
    @OrderBy("id asc")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<CategoriaModel> categorias = new ArrayList<>();

    @OneToMany(mappedBy = "balanco", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ItemBalancoModel> itens = new ArrayList<>();
}