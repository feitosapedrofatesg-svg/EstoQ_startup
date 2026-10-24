package com.estoq.business.confirmacaoEmail;

import com.estoq.business.auth.LimiteRequisicoes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class ConfirmacaoEmailController {
    private final ConfirmacaoEmailService service;
    private final LimiteRequisicoes limites;
    public record Confirmar(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {}
    public record Reenviar(@NotBlank @Email @Size(max = 254) String email) {}

    @PostMapping("/confirmar")
    public ResponseEntity<Void> confirmar(@Valid @RequestBody Confirmar dto, HttpServletRequest request) {
        limites.reservar("confirmacao:" + request.getRemoteAddr(), 60, Duration.ofHours(1));
        service.confirmar(dto.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reenviar")
    public ResponseEntity<Map<String, String>> reenviar(@Valid @RequestBody Reenviar dto, HttpServletRequest request) {
        limites.reservar("reenvio:" + request.getRemoteAddr(), 30, Duration.ofHours(1));
        service.reenviar(dto.email());
        return ResponseEntity.accepted().body(Map.of("message",
                "Se houver uma conta aguardando confirmação, enviaremos um link. Confira também o spam."));
    }
}
