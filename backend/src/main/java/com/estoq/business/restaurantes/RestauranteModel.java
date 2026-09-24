package com.estoq.business.restaurantes;

import com.estoq.core.domains.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Restaurante cadastrado na plataforma. Não é uma entidade de tenant:
 * é o próprio identificador de isolamento dos demais dados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "restaurantes")
public class RestauranteModel extends BaseModel {

    @Column(nullable = false, length = 120)
    private String nome;
}