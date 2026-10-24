package com.estoq.business.relatorios;

import com.estoq.business.sincronizacao.DadosAlteradosService;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.exceptions.ConflictException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CmvReceitaBaseService {

    private final DadosAlteradosService sincronizacao;
    private final ICmvReceitaBaseRepository repository;

    public FaturamentoPeriodoDTO faturamento(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        var porDia = new HashMap<LocalDate, CmvReceitaBaseModel>();
        repository.listarDiarios(inicio, fim).forEach(r -> porDia.put(r.getDataInicio(), r));
        var dias = new ArrayList<FaturamentoDiarioDTO>();
        var total = new BigDecimal("0.00");
        int informados = 0;
        for (var dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            var salvo = porDia.get(dia);
            var valor = salvo == null ? null : salvo.getReceitaBase();
            dias.add(new FaturamentoDiarioDTO(dia, valor, salvo == null ? null : salvo.getVersion()));
            if (valor != null) {
                informados++;
                total = total.add(valor);
            }
        }
        int pendentes = dias.size() - informados;
        // Um total antigo cobre apenas seu intervalo exato; nunca distribuir entre dias.
        var legado = pendentes == 0 ? null : obter(inicio, fim).receitaBase();
        var base = pendentes == 0 ? total : legado;
        return new FaturamentoPeriodoDTO(inicio, fim, total, informados, pendentes, base,
                legado != null, dias);
    }

    @Transactional
    public FaturamentoDiarioDTO salvarDia(FaturamentoDiarioDTO dto) {
        validarPeriodo(dto.data(), dto.data());
        if (dto.valor() != null && (dto.valor().signum() < 0 || dto.valor().scale() > 2
                || dto.valor().precision() - dto.valor().scale() > 16)) {
            throw new FieldValidationException("valor", "Informe um faturamento não negativo com até duas casas decimais.");
        }
        var entity = repository.findByDataInicioAndDataFimAndAtivoTrue(dto.data(), dto.data()).orElse(null);
        if (!Objects.equals(dto.version(), entity == null ? null : entity.getVersion())) {
            throw new ConflictException("O faturamento deste dia mudou. Recarregue e confira antes de salvar.");
        }
        if (entity == null) entity = new CmvReceitaBaseModel();
        entity.setDataInicio(dto.data());
        entity.setDataFim(dto.data());
        entity.setReceitaBase(dto.valor() == null ? null : dto.valor().setScale(2));
        repository.saveAndFlush(entity);
        sincronizacao.registrar(entity);
        return new FaturamentoDiarioDTO(dto.data(), entity.getReceitaBase(), entity.getVersion());
    }

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
        sincronizacao.registrar(entity);
        return toDto(entity);
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new FieldValidationException("periodo", "Informe um intervalo válido para o CMV.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= 3660) {
            throw new FieldValidationException("periodo", "Informe um intervalo de até 3660 dias.");
        }
    }

    private CmvReceitaBaseDTO toDto(CmvReceitaBaseModel entity) {
        return new CmvReceitaBaseDTO(entity.getDataInicio(), entity.getDataFim(), entity.getReceitaBase());
    }
}
