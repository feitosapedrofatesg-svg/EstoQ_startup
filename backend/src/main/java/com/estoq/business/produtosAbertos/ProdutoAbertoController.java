package com.estoq.business.produtosAbertos;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/produtos-abertos")
@RequiredArgsConstructor
public class ProdutoAbertoController {

    private final ProdutoAbertoService service;

    @GetMapping
    public List<ProdutoAbertoDTO> listar(@RequestParam(required = false) Long produtoId,
            @RequestParam(defaultValue = "false") Boolean finalizado) {
        return service.listar(produtoId, finalizado);
    }

    @GetMapping("/{id}")
    public ProdutoAbertoDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/abrir")
    public ResponseEntity<ProdutoAbertoDTO> abrir(@Valid @RequestBody AbrirProdutoRequestDTO d) {
        return ResponseEntity.status(201).body(service.abrirEmbalagem(d));
    }

    @PostMapping("/{id}/consumir")
    public ProdutoAbertoDTO consumir(@PathVariable Long id, @Valid @RequestBody ConsumirProdutoAbertoRequestDTO d) {
        return service.consumirProdutoAberto(id, d);
    }

    @PostMapping("/{id}/desperdicar")
    public ProdutoAbertoDTO desperdicar(@PathVariable Long id, @Valid @RequestBody DesperdicarProdutoAbertoRequestDTO d) {
        return service.desperdicarProdutoAberto(id, d);
    }
}