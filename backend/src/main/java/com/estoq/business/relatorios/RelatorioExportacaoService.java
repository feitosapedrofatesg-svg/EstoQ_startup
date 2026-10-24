package com.estoq.business.relatorios;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.helpers.HorarioOperacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RelatorioExportacaoService {
    private final PreparadorRelatorioService preparador;
    private final RelatorioPdfService pdf;
    private final RelatorioCsvService csv;
    private final RelatorioXlsService xls;
    private final HorarioOperacao horario;
    public record Arquivo(byte[] conteudo, String tipo, String nome) { }

    public Arquivo gerar(String tipo, String formato, LocalDate inicio, LocalDate fim, long dias, BigDecimal receita) {
        if (!java.util.Set.of("pdf", "csv", "xls", "xlsx").contains(formato))
            throw new FieldValidationException("formato", "Formato deve ser pdf, csv ou xls.");
        String extensao = formato.equals("xlsx") ? "xls" : formato;
        String nome = "estoQ-" + tipo + "-" + (inicio == null ? horario.hoje() : inicio) + "-a-" + (fim == null ? horario.hoje() : fim) + "." + extensao;
        if (formato.equals("pdf")) return new Arquivo(pdf.gerar(tipo, inicio, fim, dias, receita), "application/pdf", nome);
        var dados = preparador.preparar(tipo, inicio, fim, dias, receita);
        String texto = formato.equals("csv") ? csv.gerar(dados) : xls.gerar(dados);
        return new Arquivo(texto.getBytes(StandardCharsets.UTF_8), formato.equals("csv") ? "text/csv;charset=UTF-8" : "application/vnd.ms-excel;charset=UTF-8", nome);
    }
}
