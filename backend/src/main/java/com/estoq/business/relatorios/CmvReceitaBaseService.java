package com.estoq.business.relatorios;

import com.estoq.core.exceptions.FieldValidationException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CmvReceitaBaseService {

    private final ICmvReceitaBaseRepository repository;

    public CmvReceitaBaseDTO obter(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        return repository.findByDataInicioAndDataFimAndAtivoTrue(inicio, fim)
                .map(this::toDto)
                .orElseGet(() -> new CmvReceitaBaseDTO(inicio, fim, null));
    }

    @Transactional
    public CmvReceitaBaseDTO salvar(CmvReceitaBaseDTO dto) {
        validarPeriodo(dto.inicio(), dto.fim());
        if (dto.receitaBase() != null && dto.receitaBase().signum() < 0) {
            throw new FieldValidationException("receitaBase", "A receita base não pode ser negativa.");
        }
        var entity = repository.findByDataInicioAndDataFimAndAtivoTrue(dto.inicio(), dto.fim())
                .orElseGet(CmvReceitaBaseModel::new);
        entity.setDataInicio(dto.inicio());
        entity.setDataFim(dto.fim());
        entity.setReceitaBase(dto.receitaBase() == null ? null
                : dto.receitaBase().setScale(2,
                        java.math.RoundingMode.HALF_UP));
        repository.saveAndFlush(entity);
        return toDto(entity);
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new FieldValidationException("periodo", "Informe um intervalo válido para o CMV.");
        }
    }

    private CmvReceitaBaseDTO toDto(CmvReceitaBaseModel entity) {
        return new CmvReceitaBaseDTO(entity.getDataInicio(), entity.getDataFim(), entity.getReceitaBase());
    }
}