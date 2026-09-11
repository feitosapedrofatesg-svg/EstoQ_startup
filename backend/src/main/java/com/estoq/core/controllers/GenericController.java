package com.estoq.core.controllers;

import com.estoq.core.dtos.BaseDTO;
import com.estoq.core.services.IGenericService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

public abstract class GenericController<D extends BaseDTO> {

    private final IGenericService<D> service;

    protected GenericController(IGenericService<D> service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<D> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    @GetMapping
    public ResponseEntity<Page<D>> listar(Pageable pageable) {
        return ResponseEntity.ok(service.listar(pageable));
    }

    @PostMapping
    public ResponseEntity<D> criar(@Valid @RequestBody D dto) {
        return ResponseEntity.status(201).body(service.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<D> atualizar(@PathVariable Long id, @Valid @RequestBody D dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}