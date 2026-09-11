package com.estoq.business.parametrosCmv;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parametros-cmv")
@RequiredArgsConstructor
public class ParametroCmvController {

    private final ParametroCmvService service;

    @GetMapping
    public ParametroCmvDTO obter() {
        return service.obterAtual();
    }

    @PutMapping
    public ParametroCmvDTO salvar(@RequestBody ParametroCmvDTO dto) {
        return service.salvar(dto);
    }
}