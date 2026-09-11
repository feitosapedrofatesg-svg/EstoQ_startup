package com.estoq.business.configuracoesBalanco;

import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfiguracaoBalancoService {

    private final IConfiguracaoBalancoRepository repository;

    public List<ConfiguracaoBalancoDTO> listar() {
        return repository.findAllByAtivoTrueOrderByIdAsc().stream().map(this::toDto).toList();
    }

    public ConfiguracaoBalancoDTO toDto(ConfiguracaoBalancoModel c) {
        return new ConfiguracaoBalancoDTO(c.getId(), c.getVersion(), c.getPeriodicidade(), c.getDiaExecucao(), c.getProximaExecucao());
    }

    @Transactional
    public ConfiguracaoBalancoDTO salvar(ConfiguracaoBalancoDTO dto) {
        validar(dto.periodicidade(), dto.diaExecucao());
        ConfiguracaoBalancoModel entity;
        if (dto.id() == null) {
            repository.findAllByAtivoTrueOrderByIdAsc().forEach(c -> c.setAtivo(false));
            entity = new ConfiguracaoBalancoModel();
        } else {
            entity = repository.findByIdAndAtivoTrue(dto.id())
                    .orElseThrow(() -> new BusinessException("Configuração não encontrada.", HttpStatus.NOT_FOUND));
            if (dto.version() == null || !dto.version().equals(entity.getVersion())) {
                throw new ConflictException();
            }
        }
        entity.setPeriodicidade(dto.periodicidade());
        entity.setDiaExecucao(dto.diaExecucao());
        entity.setProximaExecucao(calcularProxima(dto.periodicidade(), dto.diaExecucao(), LocalDate.now()));
        repository.saveAndFlush(entity);
        return toDto(entity);
    }

    @Transactional
    public void excluir(Long id) {
        var entity = repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new BusinessException("Configuração não encontrada.", HttpStatus.NOT_FOUND));
        entity.setAtivo(false);
        repository.flush();
    }

    private void validar(PeriodicidadeBalanco periodicidade, Integer dia) {
        switch (periodicidade) {
            case DIARIA -> {
                if (dia != null) {
                    throw new FieldValidationException("diaExecucao", "Para periodicidade diária, não informe dia.");
                }
            }
            case SEMANAL -> {
                if (dia == null || dia < 1 || dia > 7) {
                    throw new FieldValidationException("diaExecucao", "Informe o dia da semana (1=segunda a 7=domingo).");
                }
            }
            case MENSAL -> {
                if (dia == null || dia < 1 || dia > 28) {
                    throw new FieldValidationException("diaExecucao", "Informe o dia do mês entre 1 e 28.");
                }
            }
        }
    }

    public LocalDate calcularProxima(PeriodicidadeBalanco periodicidade, Integer dia, LocalDate hoje) {
        return switch (periodicidade) {
            case DIARIA -> hoje.plusDays(1);
            case SEMANAL -> {
                LocalDate prox = hoje;
                for (int i = 1; i <= 7; i++) {
                    prox = hoje.plusDays(i);
                    if (prox.getDayOfWeek() == DayOfWeek.of(dia)) {
                        break;
                    }
                }
                yield prox;
            }
            case MENSAL -> {
                LocalDate base = hoje;
                LocalDate candidato = hoje.getDayOfMonth() < dia ? hoje : hoje.withDayOfMonth(1).plusMonths(1);
                int ultimo = candidato.lengthOfMonth();
                yield candidato.withDayOfMonth(Math.min(dia == null ? 1 : dia, ultimo));
            }
        };
    }

    public boolean estaVencida(ConfiguracaoBalancoModel c) {
        return c.getProximaExecucao() != null && !c.getProximaExecucao().isAfter(LocalDate.now());
    }
}