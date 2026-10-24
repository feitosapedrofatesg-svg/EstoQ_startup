package com.estoq.business.relatorios;

import com.estoq.core.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RelatorioPdfService {
    private final PreparadorRelatorioService preparador;

    public byte[] gerar(String tipo, LocalDate inicio, LocalDate fim, long dias, BigDecimal receitaBase) {
        var dados = preparador.preparar(tipo, inicio, fim, dias, receitaBase);
        try { return new LayoutRelatorioPdf().gerar(dados); }
        catch (IOException erro) { throw new BusinessException("Falha ao gerar o PDF.", HttpStatus.INTERNAL_SERVER_ERROR); }
    }
}
