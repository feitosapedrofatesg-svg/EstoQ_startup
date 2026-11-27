package com.estoq.business.alertas;

import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.usuarios.Perfil;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final AlertaService service;
    private final UsuarioAtual usuarioAtual;

    private Perfil perfilAlertas() {
        var perfil=usuarioAtual.obter().getPerfil();
        return perfil==Perfil.OPERACIONAL?Perfil.COZINHA:perfil;
    }

    @GetMapping
    public List<AlertaDTO> listar(@RequestParam(required = false) Boolean visualizado) {
        return service.listar(visualizado, perfilAlertas());
    }

    @GetMapping("/abertos")
    public long abertos() {
        return service.contarAbertos(perfilAlertas());
    }

    @PutMapping("/{id}/visualizado")
    public AlertaDTO marcarVisualizado(@PathVariable Long id) {
        return service.marcarVisualizado(id);
    }
}