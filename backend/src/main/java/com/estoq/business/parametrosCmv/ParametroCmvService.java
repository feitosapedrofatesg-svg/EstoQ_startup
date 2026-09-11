package com.estoq.business.parametrosCmv;

import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametroCmvService {

    private final IParametroCmvRepository repository;

    public ParametroCmvDTO obterAtual() {
        var atual = repository.findFirstByAtivoTrueOrderByIdDesc().orElse(null);
        if (atual == null) {
            return new ParametroCmvDTO(null, null, null);
        }
        return new ParametroCmvDTO(atual.getId(), atual.getVersion(), atual.getPercentualIdeal());
    }

    @Transactional
    public ParametroCmvDTO salvar(ParametroCmvDTO dto) {
        if (dto.percentualIdeal() == null || dto.percentualIdeal().signum() < 0 || dto.percentualIdeal().compareTo(new BigDecimal("100")) > 0) {
            throw new FieldValidationException("percentualIdeal", "Informe o percentual ideal entre 0 e 100.");
        }
        var atual = repository.findFirstByAtivoTrueOrderByIdDesc().orElse(null);
        if (atual != null && dto.version() != null && !dto.version().equals(atual.getVersion())) {
            throw new ConflictException();
        }
        if (atual != null) {
            atual.setAtivo(false);
        }
        var novo = new ParametroCmvModel();
        novo.setPercentualIdeal(dto.percentualIdeal().setScale(2));
        novo.setDataAtualizacao(LocalDateTime.now());
        repository.saveAndFlush(novo);
        return new ParametroCmvDTO(novo.getId(), novo.getVersion(), novo.getPercentualIdeal());
    }
}