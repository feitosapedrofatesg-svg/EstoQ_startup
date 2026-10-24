package com.estoq.business.registro;

import org.springframework.beans.factory.annotation.Value;

import jakarta.validation.Valid;

import com.estoq.business.auth.LimiteRequisicoes;


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
    private final LimiteRequisicoes limites;
    @Value("${estoq.cadastro.max-tentativas:10}")
    private int maxTentativas;

    @PostMapping
    public ResponseEntity<RegistroResponseDTO> registrar(@Valid @RequestBody RegistroRequestDTO dto,
            jakarta.servlet.http.HttpServletRequest request) {
        limites.reservar("cadastro:" + request.getRemoteAddr(), maxTentativas, java.time.Duration.ofHours(1));
        boolean pendente = service.registrar(dto);
        return ResponseEntity.status(201).body(new RegistroResponseDTO(pendente));
    }
}
