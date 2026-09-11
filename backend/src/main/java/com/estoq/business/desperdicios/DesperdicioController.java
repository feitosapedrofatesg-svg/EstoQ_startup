package com.estoq.business.desperdicios;

import com.estoq.business.movimentacoesEstoque.MovimentacaoResultadoDTO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/desperdicios")
@RequiredArgsConstructor
public class DesperdicioController {

    private final DesperdicioService service;

    @PostMapping
    public MovimentacaoResultadoDTO registrar(@Valid @RequestBody DesperdicioRequestDTO d) {
        return service.registrarDesperdicio(d);
    }
}