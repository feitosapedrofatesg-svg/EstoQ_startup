package com.estoq.business.lotes;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lotes")
@RequiredArgsConstructor
public class LoteController {

    private final LoteService service;

    @GetMapping
    public List<LoteDTO> listar(@RequestParam(required = false) Long produtoId) {
        return service.listar(produtoId);
    }

    @GetMapping("/{id}")
    public LoteDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @GetMapping("/vencidos")
    public List<LoteDTO> vencidos() {
        return service.vencidos();
    }

    @GetMapping("/proximos-vencimento")
    public List<LoteDTO> proximos() {
        return service.proximosVencimento();
    }
}