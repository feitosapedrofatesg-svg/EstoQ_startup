package com.estoq.business.usuarios;

import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final IUsuarioRepository repository;
    private final IUsuarioValidation validation;
    private final UsuarioAdapter adapter;
    private final PasswordEncoder encoder;

    public Page<UsuarioResponseDTO> listar(Pageable p) {
        return repository.findAll(p).map(adapter::toDto);
    }

    public UsuarioResponseDTO buscar(Long id) {
        return adapter.toDto(encontrar(id));
    }

    @Transactional
    public UsuarioResponseDTO criar(UsuarioCreateDTO dto) {
        UsuarioModel u = new UsuarioModel();
        u.setNome(dto.nome().trim());
        u.setEmail(dto.email().trim().toLowerCase(Locale.ROOT));
        u.setPerfil(dto.perfil());
        u.setSenha(encoder.encode(dto.senha()));
        validation.validate(u);
        return adapter.toDto(repository.saveAndFlush(u));
    }

    @Transactional
    public UsuarioResponseDTO atualizar(Long id, UsuarioUpdateDTO dto) {
        UsuarioModel u = encontrar(id);
        if (!Objects.equals(u.getVersion(), dto.version())) {
            throw new ConflictException();
        }
        if (!dto.ativo() || dto.perfil() != Perfil.ADMIN) {
            protegerUltimoAdmin(u);
        }
        u.setNome(dto.nome().trim());
        u.setEmail(dto.email().trim().toLowerCase(Locale.ROOT));
        u.setPerfil(dto.perfil());
        u.setAtivo(dto.ativo());
        if (dto.senha() != null) {
            u.setSenha(encoder.encode(dto.senha()));
        }
        validation.validate(u);
        repository.flush();
        return adapter.toDto(u);
    }

    @Transactional
    public void desativar(Long id) {
        UsuarioModel u = encontrar(id);
        protegerUltimoAdmin(u);
        u.setAtivo(false);
        repository.flush();
    }

    private UsuarioModel encontrar(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("Usuário não encontrado.", HttpStatus.NOT_FOUND));
    }

    private void protegerUltimoAdmin(UsuarioModel u) {
        if (u.isAtivo() && u.getPerfil() == Perfil.ADMIN && repository.countByPerfilAndAtivoTrue(Perfil.ADMIN) <= 1) {
            throw new ConflictException("Mantenha pelo menos um administrador ativo.");
        }
    }
}