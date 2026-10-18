package com.estoq.business.configuracoesBalanco;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ConfiguracaoBalancoDTO(Long id, Long version, @NotNull PeriodicidadeBalanco periodicidade, Integer diaExecucao,
        LocalDate proximaExecucao) {
}