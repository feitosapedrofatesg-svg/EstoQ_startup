package com.estoq.business.parametrosEstoque;

import com.estoq.core.helpers.IGenericAdapter;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ParametroEstoqueAdapter implements IGenericAdapter<ParametroEstoqueModel, ParametroEstoqueDTO> {

    @Override
    public ParametroEstoqueModel toEntity(ParametroEstoqueDTO d) {
        var p = new ParametroEstoqueModel();
        updateEntity(d, p);
        return p;
    }

    @Override
    public void updateEntity(ParametroEstoqueDTO d, ParametroEstoqueModel p) {
        p.setTempoReposicaoDias(d.getTempoReposicaoDias());
        p.setPeriodoAnaliseDias(d.getPeriodoAnaliseDias());
        p.setConsumoMedioDiario(d.getConsumoMedioDiario());
        p.setEstoqueMinimo(d.getEstoqueMinimo());
        p.setEstoqueMedio(d.getEstoqueMedio());
        p.setEstoqueMaximo(d.getEstoqueMaximo());
        p.setDiasAlertaVencimento(d.getDiasAlertaVencimento());
        p.setDataAtualizacao(LocalDateTime.now());
    }

    @Override
    public ParametroEstoqueDTO toDto(ParametroEstoqueModel p) {
        var d = base(p, new ParametroEstoqueDTO());
        d.setProdutoId(p.getProduto().getId());
        d.setTempoReposicaoDias(p.getTempoReposicaoDias());
        d.setPeriodoAnaliseDias(p.getPeriodoAnaliseDias());
        d.setConsumoMedioDiario(p.getConsumoMedioDiario());
        d.setEstoqueMinimo(p.getEstoqueMinimo());
        d.setEstoqueMedio(p.getEstoqueMedio());
        d.setEstoqueMaximo(p.getEstoqueMaximo());
        d.setDiasAlertaVencimento(p.getDiasAlertaVencimento());
        d.setDataAtualizacao(p.getDataAtualizacao());
        return d;
    }
}