package com.estoq.business.categorias;

import com.estoq.core.domains.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "categorias")
public class CategoriaModel extends BaseModel {

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 500)
    private String descricao;
}