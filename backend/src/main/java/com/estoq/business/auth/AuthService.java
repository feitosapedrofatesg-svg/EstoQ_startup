package com.estoq.business.auth;

import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.restaurantes.RestauranteModel;
import com.estoq.business.usuarios.UsuarioModel;

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
    private final IRestauranteRepository restaurantes;

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
        return montar();
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
        return montar();
    }

    /** Resolve o usuário da sessão e o nome da loja em que ele está operando. */
    private AuthenticatedUserDTO montar() {
        UsuarioModel u = usuarioAtual.obter();
        Long restauranteId = u.getRestauranteId();
        String nomeLoja = restauranteId == null || restauranteId <= 0 ? null
                : restaurantes.findById(restauranteId).map(RestauranteModel::getNome).orElse(null);
        return AuthenticatedUserDTO.of(u, nomeLoja);
    }
}