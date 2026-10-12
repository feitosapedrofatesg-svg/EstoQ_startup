package com.estoq.config.security;

import com.estoq.business.auth.UsuarioPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Desativação, troca de senha, troca de perfil ou loja suspensa revogam também sessões já abertas. */
public class UsuarioAtivoFilter extends OncePerRequestFilter {

    private final SessaoValidaService sessoes;

    public UsuarioAtivoFilter(SessaoValidaService sessoes) { this.sessoes = sessoes; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal p) {
            boolean valido = sessoes.validar(p);
            if (!valido) {
                SecurityContextHolder.clearContext();
                if (req.getSession(false) != null) {
                    req.getSession(false).invalidate();
                }
            }
        }
        chain.doFilter(req, res);
    }

}
