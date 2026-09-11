package com.estoq.business.produtos;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estoque")
@RequiredArgsConstructor
public class EstoqueController {

    private final EstoqueService service;

    @GetMapping
    public List<EstoqueDTO> listar(@RequestParam(defaultValue = "false") boolean somenteAbertos,
            @RequestParam(defaultValue = "false") boolean somenteBaixo) {
        return service.listar(somenteAbertos, somenteBaixo);
    }
}