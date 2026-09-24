package com.estoq.config.security;

import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.restaurantes.RestauranteModel;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.UsuarioModel;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Desativação, troca de senha, troca de perfil ou loja suspensa revogam também sessões já abertas. */
public class UsuarioAtivoFilter extends OncePerRequestFilter {

    private final IUsuarioRepository repository;
    private final IRestauranteRepository restaurantes;

    public UsuarioAtivoFilter(IUsuarioRepository repository, IRestauranteRepository restaurantes) {
        this.repository = repository;
        this.restaurantes = restaurantes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal p) {
            boolean valido = repository.findByIdAndAtivoTrue(p.id())
                    .map(u -> u.getEmail().equals(p.email()) && u.getSenha().equals(p.senha())
                            && u.getPerfil().name().equals(p.perfil()) && restauranteAtivo(u))
                    .orElse(false);
            if (!valido) {
                SecurityContextHolder.clearContext();
                if (req.getSession(false) != null) {
                    req.getSession(false).invalidate();
                }
            }
        }
        chain.doFilter(req, res);
    }

    private boolean restauranteAtivo(UsuarioModel usuario) {
        Long restauranteId = usuario.getRestauranteId();
        if (restauranteId == null || restauranteId == 0L) {
            return true;
        }
        return restaurantes.findById(restauranteId).map(RestauranteModel::isAtivo).orElse(false);
    }
}