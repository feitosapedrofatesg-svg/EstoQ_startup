package com.estoq.business.configuracoesBalanco;

import java.time.LocalDate;

public record ConfiguracaoBalancoDTO(Long id, Long version, PeriodicidadeBalanco periodicidade, Integer diaExecucao,
        LocalDate proximaExecucao) {
}