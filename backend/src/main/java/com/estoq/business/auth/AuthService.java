package com.estoq.business.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfRepository;
    private final UsuarioAtual usuarioAtual;
    private final LoginAttemptService tentativas;

    public AuthenticatedUserDTO autenticar(LoginRequestDTO dto, HttpServletRequest request, HttpServletResponse response) {
        var ip = request.getRemoteAddr();
        tentativas.verificar(dto.email(), ip);
        var auth = autenticar(dto, ip);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        csrfRepository.saveToken(null, request, response);
        return AuthenticatedUserDTO.of(usuarioAtual.obter());
    }

    private Authentication autenticar(LoginRequestDTO dto, String ip) {
        try {
            var auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(dto.email().trim(), dto.senha()));
            tentativas.registrarSucesso(dto.email(), ip);
            return auth;
        } catch (AuthenticationException e) {
            tentativas.registrarFalha(dto.email(), ip);
            throw e;
        }
    }

    public AuthenticatedUserDTO me() {
        return AuthenticatedUserDTO.of(usuarioAtual.obter());
    }
}