package com.estoq.business.relatorios;

import com.estoq.core.exceptions.FieldValidationException;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.core.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static com.estoq.business.relatorios.DocumentoRelatorioPdf.*;

@Service
@RequiredArgsConstructor
public class RelatorioPdfService {

    private final HorarioOperacao horario;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");
    private final RelatorioService relatorios;

    public byte[] gerar(String tipo, LocalDate inicio, LocalDate fim, long dias, BigDecimal receitaBase) {
        var periodoInicio = inicio == null ? horario.hoje().minusDays(30) : inicio;
        var periodoFim = fim == null ? horario.hoje() : fim;
        var de = horario.inicioUtc(periodoInicio);
        var ate = horario.inicioUtc(periodoFim.plusDays(1));
        if (!de.isBefore(ate) || java.time.temporal.ChronoUnit.DAYS.between(de, ate) > 3660)
            throw new FieldValidationException("periodo", "Informe um intervalo válido de até dez anos.");
        String periodo = "Período: " + periodoInicio.format(DATA) + " a " + periodoFim.format(DATA);
        String observacao = null;
        Destaque total = null;
        var linhas = new ArrayList<List<String>>();
        var resumo = new ArrayList<Destaque>();
        List<Coluna> colunas;
        String titulo;
        // As consultas e cálculos continuam nos serviços existentes; aqui só formatamos.
        switch (tipo) {
            case "estoque-atual" -> {
                titulo = "Estoque atual";
                colunas = List.of(c("Produto", 4), c("Categoria", 2.5f), n("Saldo", 2), n("Valor em estoque", 2));
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.estoqueAtual()) {
                    linhas.add(java.util.Arrays.asList(l.produtoNome(), l.categoriaNome(), qtd(l.saldoAtual(), l.unidadeMedida().name()), moeda(l.valorEstoque())));
                    valorTotal = valorTotal.add(valorMonetario(l.valorEstoque()));
                }
                total = new Destaque("Valor total em estoque", moeda(valorTotal));
            }
            case "proximos-vencimento" -> {
                titulo = "Lotes próximos do vencimento";
                colunas = List.of(c("Produto", 3), c("Lote", 2.5f), c("Validade", 1.5f), n("Dias restantes", 1.3f), n("Saldo", 1.8f));
                for (var l : relatorios.proximosVencimento()) linhas.add(java.util.Arrays.asList(l.produtoNome(), l.codigo(), data(l.dataValidade()), l.diasParaVencimento() == null ? "-" : l.diasParaVencimento().toString(), qtd(l.quantidadeAtual(), l.unidadeMedida().name())));
                observacao = "Atenção: lotes próximos da validade. Confira a data antes de utilizar.";
            }
            case "vencidos" -> {
                titulo = "Lotes vencidos";
                colunas = List.of(c("Produto", 3), c("Lote", 2.5f), c("Validade", 1.5f), n("Saldo", 1.8f), n("Valor do saldo", 2));
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.vencidos()) {
                    var valor = l.quantidadeAtual().multiply(l.precoUnitario());
                    linhas.add(java.util.Arrays.asList(l.produtoNome(), l.codigo(), data(l.dataValidade()), qtd(l.quantidadeAtual(), l.unidadeMedida().name()), moeda(valor)));
                    valorTotal = valorTotal.add(valorMonetario(valor));
                }
                total = new Destaque("Valor total dos lotes vencidos", moeda(valorTotal));
                observacao = "VENCIDO: os lotes desta lista ultrapassaram sua validade.";
            }
            case "produtos-abertos" -> {
                titulo = "Embalagens abertas";
                colunas = List.of(c("Produto", 3), c("Lote", 2.5f), c("Abertura", 1.8f), n("Quantidade aberta", 2), n("Restante", 2));
                for (var p : relatorios.produtosAbertos()) linhas.add(java.util.Arrays.asList(p.produtoNome(), p.loteCodigo(), p.dataAbertura() == null ? "-" : p.dataAbertura().toLocalDate().format(DATA), qtd(p.quantidadeAberta(), p.unidadeMedida()), qtd(p.quantidadeRestante(), p.unidadeMedida())));
            }
            case "desperdicio" -> {
                titulo = "Desperdício no período";
                colunas = List.of(c("Data e hora", 1.9f), c("Produto", 3), c("Motivo", 2.8f), n("Quantidade", 1.5f), n("Prejuízo", 1.9f));
                var perdas = relatorios.desperdicio(de, ate);
                var valorTotal = BigDecimal.ZERO;
                for (var d : perdas) {
                    // A descrição é opcional; o motivo cadastrado continua identificando a perda.
                    String motivo = d.descricaoMotivo();
                    if (motivo == null || motivo.isBlank()) motivo = d.motivo() == null ? "Não informado" : d.motivo().name().replace('_', ' ');
                    linhas.add(java.util.Arrays.asList(d.dataHora().atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), d.produtoNome(), motivo, qtd(d.quantidade(), d.unidadeMedida()), moeda(d.valorPrejuizo())));
                    valorTotal = valorTotal.add(valorMonetario(d.valorPrejuizo()));
                }
                total = new Destaque("Prejuízo total no período", moeda(valorTotal));
                observacao = "Quantidades na unidade de cadastro de cada produto; quando indisponível, a unidade não é indicada. Não somar unidades diferentes. Horários em UTC-3.";
            }
            case "consumo-medio" -> {
                titulo = "Consumo médio";
                if (dias < 1 || dias > 3660)
                    throw new FieldValidationException("dias", "Informe de 1 a 3660 dias.");
                var inicioConsumo = horario.inicioUtc(periodoFim.plusDays(1).minusDays(dias));
                periodo = "Período: " + horario.diaLocal(inicioConsumo).format(DATA) + " a " + periodoFim.format(DATA);
                colunas = List.of(c("Produto", 4), n("Total consumido", 2.5f), n("Dias", 1), n("Média por dia", 2.5f));
                for (var c : relatorios.consumoMedio(inicioConsumo, ate)) linhas.add(java.util.Arrays.asList(c.produtoNome(), qtd(c.totalConsumidoPeriodo(), c.unidadeMedida()), Long.toString(c.diasPeriodo()), qtd(c.consumoMedioDiario(), c.unidadeMedida())));
            }
            case "cmv" -> {
                titulo = "Custo da mercadoria vendida (CMV)";
                colunas = List.of(c("Composição e indicadores", 6), n("Valor", 3));
                var cmv = relatorios.calcularCmv(de, ate, receitaBase);
                resumo.add(new Destaque("CMV do período", moeda(cmv.cmv())));
                if (cmv.receitaBase() != null) resumo.add(new Destaque("Percentual de CMV", percentual(cmv.cmvPercentual())));
                linhas.add(java.util.Arrays.asList("Estoque no início do período", moeda(cmv.valorEstoqueInicial())));
                linhas.add(java.util.Arrays.asList("+ Compras no período", moeda(cmv.valorCompras())));
                linhas.add(java.util.Arrays.asList("- Estoque no fim do período", moeda(cmv.valorEstoqueFinal())));
                linhas.add(java.util.Arrays.asList("Consumo registrado", moeda(cmv.valorConsumoRegistrado())));
                linhas.add(java.util.Arrays.asList("Desperdício", moeda(cmv.valorDesperdicio())));
                linhas.add(java.util.Arrays.asList("Perdas não explicadas", moeda(cmv.valorPerdasNaoExplicadas())));
                if (cmv.receitaBase() != null) {
                    linhas.add(java.util.Arrays.asList("Receita base informada", moeda(cmv.receitaBase())));
                    linhas.add(java.util.Arrays.asList("Percentual ideal de CMV", percentual(cmv.percentualIdeal())));
                }
                observacao = "CMV = estoque inicial + compras - estoque final. Valores apurados pelo sistema para o período consultado.";
            }
            case "cmv-mensal" -> {
                titulo = "CMV mês a mês";
                colunas = List.of(c("Mês", 3), n("CMV", 3));
                for (var m : relatorios.cmvPorMes(de, ate)) linhas.add(java.util.Arrays.asList(m.periodo().format(DateTimeFormatter.ofPattern("MM/yyyy")), moeda(m.cmv())));
                observacao = "Os meses nas bordas do intervalo podem representar períodos parciais.";
            }
            case "reposicao-sugerida" -> {
                titulo = "Reposição sugerida";
                colunas = List.of(c("Produto", 4), n("Saldo atual", 2.5f), n("Quantidade sugerida", 2.5f), n("Prazo (dias)", 1.5f));
                for (var r : relatorios.reposicaoSugerida(null)) linhas.add(java.util.Arrays.asList(r.produtoNome(), qtd(r.saldoAtual(), r.unidadeMedida().name()), qtd(r.quantidadeSugerida(), r.unidadeMedida().name()), Integer.toString(r.diasReposicao())));
            }
            default -> throw new BusinessException("Relatório PDF não suportado: " + tipo, HttpStatus.BAD_REQUEST);
        }
        if (List.of("estoque-atual", "proximos-vencimento", "vencidos", "produtos-abertos", "reposicao-sugerida").contains(tipo)) {
            periodo = "Posição atual em " + horario.hoje().format(DATA) + " (não é um estoque histórico)";
        }
        try { return new LayoutRelatorioPdf().gerar(new DocumentoRelatorioPdf(titulo, periodo, colunas, linhas, resumo, observacao, total)); }
        catch (IOException e) { throw new BusinessException("Falha ao gerar o PDF.", HttpStatus.INTERNAL_SERVER_ERROR); }
    }

    private static Coluna c(String titulo, float peso) { return new Coluna(titulo, peso, false); }
    private static Coluna n(String titulo, float peso) { return new Coluna(titulo, peso, true); }
    private static String data(LocalDate valor) { return valor == null ? "-" : valor.format(DATA); }
    private static String qtd(BigDecimal valor, String unidade) { return numero(valor) + (unidade == null || unidade.isBlank() ? "" : " " + unidade.toLowerCase(BRASIL)); }
    private static String numero(BigDecimal valor) {
        if (valor == null) return "-";
        var formato = NumberFormat.getNumberInstance(BRASIL);
        formato.setMaximumFractionDigits(Math.max(3, valor.scale()));
        return formato.format(valor);
    }
    /** Soma os valores monetários exibidos, sem misturar quantidades de unidades diferentes. */
    private static BigDecimal valorMonetario(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    private static String moeda(BigDecimal valor) {
        var formato = NumberFormat.getCurrencyInstance(BRASIL);
        formato.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return formato.format(valor == null ? BigDecimal.ZERO : valor);
    }
    private static String percentual(BigDecimal valor) { return valor == null ? "Não informado" : numero(valor) + "%"; }
}
