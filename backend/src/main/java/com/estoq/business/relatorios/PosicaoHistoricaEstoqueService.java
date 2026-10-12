package com.estoq.business.relatorios;

import com.estoq.business.lotes.ILoteRepository;
import com.estoq.core.exceptions.FieldValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static com.estoq.core.helpers.NumeroUtil.money;

/** Desfaz movimentações posteriores ao corte sem modificar o estoque armazenado. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosicaoHistoricaEstoqueService {
    private final ILoteRepository lotes;

    public record Valores(BigDecimal inicial, BigDecimal finalPeriodo) {}

    public Valores obter(LocalDateTime inicio, LocalDateTime fim) {
        validarPeriodo(inicio, fim);
        var inicial = BigDecimal.ZERO;
        var finalPeriodo = BigDecimal.ZERO;
        for (var lote : lotes.posicoesHistoricas(inicio, fim)) {
            // Quantidades e preços conservam a precisão do domínio até a soma final.
            inicial = inicial.add(lote.getAtual().subtract(lote.getDeltaInicio()).max(BigDecimal.ZERO).multiply(lote.getPreco()));
            finalPeriodo = finalPeriodo.add(lote.getAtual().subtract(lote.getDeltaFim()).max(BigDecimal.ZERO).multiply(lote.getPreco()));
        }
        return new Valores(money(inicial), money(finalPeriodo));
    }

    public void validarPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new FieldValidationException("periodo", "Informe um intervalo válido para o relatório.");
        }
    }
}
