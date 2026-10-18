package com.estoq.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

/** Resposta pública pequena, sem detalhes do banco e sem criar sessão. */
@RestController
@RequiredArgsConstructor
public class SaudeController {
    private final DataSource banco;

    @GetMapping("/api/saude/vivo")
    public Map<String, String> vivo() { return Map.of("status", "ok"); }

    @GetMapping("/api/saude/pronto")
    public ResponseEntity<Map<String, String>> pronto() {
        try (var conexao = banco.getConnection()) {
            if (conexao.isValid(2)) return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (SQLException erro) {
            // Não publicar host, usuário ou mensagem de conexão no endpoint público.
        }
        return ResponseEntity.status(503).body(Map.of("status", "indisponivel"));
    }
}
