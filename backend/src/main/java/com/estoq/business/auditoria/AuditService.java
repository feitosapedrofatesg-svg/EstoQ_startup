package com.estoq.business.auditoria;

import com.estoq.business.usuarios.UsuarioModel;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final IAuditoriaRepository repository;

    @Transactional
    public void registrar(String tipo, String entidade, String entidadeId, String descricao, UsuarioModel usuario) {
        AuditoriaModel a = new AuditoriaModel();
        a.setTipo(tipo);
        a.setEntidade(entidade);
        a.setEntidadeId(entidadeId);
        a.setDescricao(descricao != null && descricao.length() > 400 ? descricao.substring(0, 400) : descricao);
        a.setUsuarioNome(usuario != null ? usuario.getNome() : "sistema");
        a.setDataHora(LocalDateTime.now());
        repository.save(a);
    }
}