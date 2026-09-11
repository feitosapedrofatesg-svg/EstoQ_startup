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

    public void responder(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ErrorResponse.error(mensagem)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> login(AuthenticationException ex) {
        return ResponseEntity.status(401).body(ErrorResponse.error("E-mail ou senha inválidos, ou usuário inativo."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> negado(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ErrorResponse.error("Seu perfil não possui permissão para esta operação."));
    }
}