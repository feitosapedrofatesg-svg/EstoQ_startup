package com.estoq.config.security;

import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.restaurantes.RestauranteModel;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.UsuarioModel;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final IUsuarioRepository repository;
    private final IRestauranteRepository restaurantes;

    @Override
    public UserDetails loadUserByUsername(String email) {
        var usuario = repository.findByEmailIgnoreCaseAndAtivoTrue(email)
                .orElseThrow(() -> new UsernameNotFoundException("E-mail ou senha inválidos."));
        if (!restauranteAtivo(usuario)) {
            throw new UsernameNotFoundException("E-mail ou senha inválidos.");
        }
        return UsuarioPrincipal.of(usuario);
    }

    private boolean restauranteAtivo(UsuarioModel usuario) {
        Long restauranteId = usuario.getRestauranteId();
        if (restauranteId == null || restauranteId == 0L) {
            return true;
        }
        return restaurantes.findById(restauranteId).map(RestauranteModel::isAtivo).orElse(false);
    }
}