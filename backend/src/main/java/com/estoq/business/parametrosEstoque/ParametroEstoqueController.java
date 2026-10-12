package com.estoq.business.parametrosEstoque;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.estoq.core.controllers.GenericController;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parametros-estoque")
public class ParametroEstoqueController extends GenericController<ParametroEstoqueDTO> {

    private final ParametroEstoqueService service;

    public ParametroEstoqueController(ParametroEstoqueService service) {
        super(service);
        this.service = service;
    }
    @GetMapping("/por-produto/{produtoId}")
    public ParametroEstoqueDTO buscarPorProduto(@PathVariable Long produtoId) {
        return service.buscarPorProduto(produtoId);
    }
}
