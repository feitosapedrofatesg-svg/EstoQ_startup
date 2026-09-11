package com.estoq.business.consumos;

import com.estoq.business.movimentacoesEstoque.MovimentacaoResultadoDTO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consumos")
@RequiredArgsConstructor
public class ConsumoController {

    private final ConsumoService service;

    @PostMapping
    public MovimentacaoResultadoDTO registrar(@Valid @RequestBody ConsumoRequestDTO d) {
        return service.registrarConsumo(d);
    }
}