package com.estoq.config.security;

import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.restaurantes.IRestauranteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** A mesma validação protege requisições normais e conexões de eventos já abertas. */
@Service
@RequiredArgsConstructor
public class SessaoValidaService {
    private final IUsuarioRepository usuarios;
    private final IRestauranteRepository restaurantes;

    @Transactional(readOnly = true)
    public boolean validar(UsuarioPrincipal p) {
        return usuarios.findByIdAndAtivoTrue(p.id()).map(u -> u.getEmail().equals(p.email())
                && Objects.equals(u.getSenha(), p.senha()) && Objects.equals(u.getGoogleSub(), p.googleSub())
                && u.getPerfil().name().equals(p.perfil())
                && !u.emailPendente() && Objects.equals(u.getRestauranteId(), p.restauranteId())
                && (p.restauranteId() == null || p.restauranteId() == 0
                    || restaurantes.findById(p.restauranteId()).map(r -> r.isAtivo()).orElse(false))).orElse(false);
    }
}
