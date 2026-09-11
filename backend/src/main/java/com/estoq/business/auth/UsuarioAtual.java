package com.estoq.business.auth;

import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioAtual {

    private final IUsuarioRepository repository;

    @Transactional(readOnly = true)
    public UsuarioModel obter() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new BusinessException("Autenticação necessária.", HttpStatus.UNAUTHORIZED);
        }
        return repository.findByEmailIgnoreCaseAndAtivoTrue(auth.getName())
                .orElseThrow(() -> new BusinessException("Sessão inválida ou usuário inativo.", HttpStatus.UNAUTHORIZED));
    }
}