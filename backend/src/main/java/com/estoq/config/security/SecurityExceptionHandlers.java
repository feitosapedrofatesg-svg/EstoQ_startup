package com.estoq.config.security;

import com.estoq.core.exceptions.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RestControllerAdvice
@Order(-1)
@RequiredArgsConstructor
public class SecurityExceptionHandlers {

    private final ObjectMapper mapper;
    private final org.springframework.core.env.Environment env;
    private final org.springframework.context.ApplicationContext app;

    /** DIAGNÓSTICO TEMPORÁRIO (2026-09-24): estado da migração para depurar o deploy. */
    public String diagFlyway() {
        var sb = new StringBuilder();
        try {
            sb.append("flywayBean=").append(app.containsBean("flyway"));
            sb.append(";flywayEnabled=").append(env.getProperty("spring.flyway.enabled"));
            sb.append(";flywayLocations=").append(env.getProperty("spring.flyway.locations"));
            sb.append(";ddlAuto=").append(env.getProperty("spring.jpa.hibernate.ddl-auto"));
            sb.append(";url=").append(env.getProperty("spring.datasource.url"));
            if (app.containsBean("flyway")) {
                var fy = (org.flywaydb.core.Flyway) app.getBean("flyway");
                sb.append(";flywaySchema=").append(fy.getConfiguration().getDefaultSchema());
                var aplicadas = fy.info().applied();
                sb.append(";aplicadas=").append(aplicadas.length);
                for (var m : aplicadas) {
                    sb.append(";").append(m.getVersion() == null ? "baseline" : m.getVersion()).append(":").append(m.getDescription());
                }
            }
        } catch (Exception e) {
            sb.append(";diagErro=").append(e.getClass().getSimpleName()).append(":").append(e.getMessage());
        }
        return sb.toString();
    }

    public void responder(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ErrorResponse.error(mensagem)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> login(AuthenticationException ex) {
        // DIAGNÓSTICO TEMPORÁRIO (2026-09-24): expor a exceção real de autenticação.
        return ResponseEntity.status(401).body(ErrorResponse.error(
                "E-mail ou senha inválidos, ou usuário inativo. [DIAG] "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage()
                        + " || " + diagFlyway()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> negado(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ErrorResponse.error("Seu perfil não possui permissão para esta operação."));
    }
}