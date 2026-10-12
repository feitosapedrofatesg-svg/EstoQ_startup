package com.estoq.business.sincronizacao;

import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.core.exceptions.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class SincronizacaoController {
    private final SincronizacaoService service;

    @GetMapping(value = "/api/sincronizacao/eventos", produces = "text/event-stream")
    public ResponseEntity<SseEmitter> eventos(HttpServletRequest request, @AuthenticationPrincipal UsuarioPrincipal usuario) {
        if (usuario == null || usuario.restauranteId() == null || usuario.restauranteId() <= 0)
            throw new BusinessException("A sincronização exige uma sessão de restaurante.", HttpStatus.FORBIDDEN);
        return ResponseEntity.ok().header("Cache-Control", "no-store, no-transform")
                .header("X-Accel-Buffering", "no").body(service.conectar(request.getSession(false), usuario));
    }
}
