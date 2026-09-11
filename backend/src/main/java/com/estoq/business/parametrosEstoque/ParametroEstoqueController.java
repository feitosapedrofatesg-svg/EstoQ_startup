package com.estoq.business.parametrosEstoque;

import com.estoq.core.controllers.GenericController;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parametros-estoque")
public class ParametroEstoqueController extends GenericController<ParametroEstoqueDTO> {

    public ParametroEstoqueController(ParametroEstoqueService service) {
        super(service);
    }
}