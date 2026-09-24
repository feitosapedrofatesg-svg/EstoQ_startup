package com.estoq.business.registro;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/registro")
public class RegistroController {

    private final RegistroService service;

    @PostMapping
    public ResponseEntity<Void> registrar(@Valid @RequestBody RegistroRequestDTO dto) {
        service.registrar(dto);
        return ResponseEntity.status(201).build();
    }
}