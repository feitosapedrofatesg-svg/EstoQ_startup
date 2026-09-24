package com.estoq.business.plataforma;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plataforma")
public class PlataformaController {

    private final PlataformaService service;

    @GetMapping("/restaurantes")
    public List<RestauranteDTO> listar() {
        return service.listar();
    }

    @PutMapping("/restaurantes/{id}")
    public void atualizarStatus(@PathVariable Long id, @Valid @RequestBody AtualizarRestauranteStatusDTO dto) {
        service.atualizarStatus(id, dto.ativo());
    }

    @PostMapping("/restaurantes/{id}/redefinir-admin")
    public void redefinirSenhaAdmin(@PathVariable Long id, @Valid @RequestBody RedefinirSenhaAdminDTO dto) {
        service.redefinirSenhaAdmin(id, dto.senha());
    }
}