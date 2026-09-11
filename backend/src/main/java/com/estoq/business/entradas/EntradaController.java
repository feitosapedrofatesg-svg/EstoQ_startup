package com.estoq.business.entradas;

import com.estoq.business.movimentacoesEstoque.MovimentacaoResultadoDTO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entradas")
@RequiredArgsConstructor
public class EntradaController {

    private final EntradaService service;

    @PostMapping
    public ResponseEntity<MovimentacaoResultadoDTO> registrar(@Valid @RequestBody EntradaRequestDTO d) {
        return ResponseEntity.status(201).body(service.registrarEntrada(d));
    }
}