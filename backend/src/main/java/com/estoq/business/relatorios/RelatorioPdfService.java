package com.estoq.business.relatorios;

import com.estoq.core.exceptions.FieldValidationException;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.core.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import static com.estoq.business.relatorios.DocumentoRelatorioPdf.*;
import static com.estoq.business.relatorios.FormatoRelatorioPdf.*;

@Service
@RequiredArgsConstructor
public class RelatorioPdfService {

    private final HorarioOperacao horario;
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
        var totais = new TotaisRelatorioPdf();
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
                    totais.quantidade("Saldo total", l.saldoAtual(), l.unidadeMedida().name());
                }
                totais.adicionar("Valor total em estoque", moeda(valorTotal));
            }
            case "proximos-vencimento" -> {
                titulo = "Lotes próximos do vencimento";
                colunas = List.of(c("Produto", 3), c("Lote", 2), c("Validade", 1.5f), n("Dias restantes", 1.3f), n("Saldo", 1.8f), n("Valor do saldo", 2));
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.proximosVencimento()) {
                    var valor = l.quantidadeAtual().multiply(l.precoUnitario());
                    linhas.add(java.util.Arrays.asList(l.produtoNome(), l.codigo(), data(l.dataValidade()), l.diasParaVencimento() == null ? "-" : l.diasParaVencimento().toString(), qtd(l.quantidadeAtual(), l.unidadeMedida().name()), moeda(valor)));
                    valorTotal = valorTotal.add(valorMonetario(valor));
                    totais.quantidade("Saldo total", l.quantidadeAtual(), l.unidadeMedida().name());
                }
                totais.adicionar("Valor total próximo do vencimento", moeda(valorTotal));
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
                    totais.quantidade("Saldo total", l.quantidadeAtual(), l.unidadeMedida().name());
                }
                totais.adicionar("Valor total dos lotes vencidos", moeda(valorTotal));
                observacao = "VENCIDO: os lotes desta lista ultrapassaram sua validade.";
            }
            case "produtos-abertos" -> {
                titulo = "Embalagens abertas";
                colunas = List.of(c("Produto", 3), c("Lote", 2.5f), c("Abertura", 1.8f), n("Quantidade aberta", 2), n("Restante", 2));
                for (var p : relatorios.produtosAbertos()) {
                    linhas.add(java.util.Arrays.asList(p.produtoNome(), p.loteCodigo(), p.dataAbertura() == null ? "-" : horario.diaLocal(p.dataAbertura()).format(DATA), qtd(p.quantidadeAberta(), p.unidadeMedida()), qtd(p.quantidadeRestante(), p.unidadeMedida())));
                    totais.quantidade("Quantidade aberta total", p.quantidadeAberta(), p.unidadeMedida());
                    totais.quantidade("Quantidade restante total", p.quantidadeRestante(), p.unidadeMedida());
                }
                totais.adicionar("Embalagens abertas", Integer.toString(linhas.size()));
                observacao = "O restante das embalagens já faz parte do saldo dos lotes. Não somar novamente ao estoque.";
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
                    totais.quantidade("Quantidade perdida total", d.quantidade(), d.unidadeMedida());
                }
                totais.adicionar("Prejuízo total no período", moeda(valorTotal));
                observacao = "Quantidades na unidade de cadastro de cada produto; quando indisponível, a unidade não é indicada. Não somar unidades diferentes. Horários em UTC-3.";
            }
            case "consumo-medio" -> {
                titulo = "Consumo médio";
                if (dias < 1 || dias > 3660)
                    throw new FieldValidationException("dias", "Informe de 1 a 3660 dias.");
                var inicioConsumo = horario.inicioUtc(periodoFim.plusDays(1).minusDays(dias));
                periodo = "Período: " + horario.diaLocal(inicioConsumo).format(DATA) + " a " + periodoFim.format(DATA);
                colunas = List.of(c("Produto", 4), n("Total consumido", 2.5f), n("Dias", 1), n("Média por dia", 2.5f));
                for (var c : relatorios.consumoMedio(inicioConsumo, ate)) {
                    linhas.add(java.util.Arrays.asList(c.produtoNome(), qtd(c.totalConsumidoPeriodo(), c.unidadeMedida()), Long.toString(c.diasPeriodo()), qtd(c.consumoMedioDiario(), c.unidadeMedida())));
                    totais.quantidade("Total consumido", c.totalConsumidoPeriodo(), c.unidadeMedida());
                }
                totais.adicionar("Produtos com consumo", Integer.toString(linhas.size()));
                observacao = "Médias e dias não são somados. Quantidades sem unidade identificada permanecem apenas por produto.";
            }
            case "consumo-dia-semana" -> {
                titulo = "Consumo por dia da semana";
                colunas = List.of(c("Dia da semana", 4), n("Custo total", 3), n("Custo médio por ocorrência", 3));
                var valorTotal = BigDecimal.ZERO;
                for (var dia : relatorios.consumoPorDiaSemana(de, ate)) {
                    linhas.add(List.of(dia.diaSemana().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("pt-BR")), moeda(dia.valorTotal()), moeda(dia.valorMedio())));
                    valorTotal = valorTotal.add(valorMonetario(dia.valorTotal()));
                }
                totais.adicionar("Custo total de consumo no período", moeda(valorTotal));
                observacao = "A média considera quantas vezes cada dia da semana ocorreu no intervalo, incluindo dias sem consumo. Médias não são somadas.";
            }
            case "faturamento" -> {
                titulo = "Faturamento no período";
                colunas = List.of(c("Data", 3), n("Faturamento", 3), c("Situação", 3));
                var faturamento = relatorios.faturamento(periodoInicio, periodoFim);
                for (var dia : faturamento.dias()) {
                    linhas.add(List.of(data(dia.data()), dia.valor() == null ? "Não informado" : moeda(dia.valor()), dia.valor() == null ? "Pendente" : "Informado"));
                }
                totais.adicionar(faturamento.diasPendentes() > 0 ? "Faturamento informado (parcial)" : "Faturamento total do período", moeda(faturamento.totalInformado()));
                totais.adicionar("Dias informados", Integer.toString(faturamento.diasInformados()));
                totais.adicionar("Dias pendentes", Integer.toString(faturamento.diasPendentes()));
                observacao = "R$ 0 significa dia informado sem vendas. Não informado significa pendência; o total considera somente os dias informados.";
                if (faturamento.receitaPorPeriodo()) {
                    resumo.add(new Destaque("Total histórico do intervalo (referência)", moeda(faturamento.receitaBase())));
                    observacao += " O total histórico foi cadastrado por período e não é somado nem distribuído entre os dias.";
                }
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
                linhas.add(List.of("Percentual de CMV", percentual(cmv.cmvPercentual())));
                linhas.add(List.of("Percentual de desperdício sobre o CMV", percentual(cmv.percentualDesperdicioSobreCmv())));
                linhas.add(List.of("Percentual ideal de CMV", percentual(cmv.percentualIdeal())));
                linhas.add(List.of("Diferença para a meta (pontos percentuais)", cmv.diferencaPercentualParaMeta() == null ? "Indisponível" : numero(cmv.diferencaPercentualParaMeta()) + " p.p."));
                if (cmv.receitaBase() != null) {
                    linhas.add(java.util.Arrays.asList("Faturamento do período", moeda(cmv.receitaBase())));
                }
                totais.adicionar("CMV total do período", moeda(cmv.cmv()));
                observacao = "CMV = estoque inicial + compras - estoque final. Valores apurados pelo sistema para o período consultado.";
                if (cmv.receitaPorPeriodo()) {
                    observacao += " Faturamento informado como total do período, preservado do cadastro anterior.";
                } else if (cmv.diasFaturamentoPendentes() > 0) {
                    linhas.add(java.util.Arrays.asList("Faturamento diário já informado", moeda(cmv.faturamentoInformado())));
                    linhas.add(java.util.Arrays.asList("Dias sem faturamento informado", Integer.toString(cmv.diasFaturamentoPendentes())));
                    observacao += " Percentual pendente: complete o faturamento dos dias do período.";
                } else if (cmv.receitaBase() != null && cmv.receitaBase().signum() == 0) {
                    observacao += " Percentual indisponível: faturamento total igual a zero.";
                }
            }
            case "cmv-mensal" -> {
                titulo = "CMV mês a mês";
                colunas = List.of(c("Mês", 2), n("CMV", 3), n("Compras", 3), n("Desperdício", 3));
                var cmvTotal = BigDecimal.ZERO;
                var comprasTotal = BigDecimal.ZERO;
                var desperdicioTotal = BigDecimal.ZERO;
                for (var m : relatorios.cmvPorMes(de, ate)) {
                    linhas.add(List.of(m.periodo().format(DateTimeFormatter.ofPattern("MM/yyyy")), moeda(m.cmv()), moeda(m.valorCompras()), moeda(m.valorDesperdicio())));
                    cmvTotal = cmvTotal.add(valorMonetario(m.cmv()));
                    comprasTotal = comprasTotal.add(valorMonetario(m.valorCompras()));
                    desperdicioTotal = desperdicioTotal.add(valorMonetario(m.valorDesperdicio()));
                }
                totais.adicionar("CMV total no período", moeda(cmvTotal));
                totais.adicionar("Compras totais no período", moeda(comprasTotal));
                totais.adicionar("Desperdício total no período", moeda(desperdicioTotal));
                observacao = "Os meses nas bordas do intervalo podem representar períodos parciais.";
            }
            case "reposicao-sugerida" -> {
                titulo = "Reposição sugerida";
                colunas = List.of(c("Produto", 4), n("Saldo atual", 2.5f), n("Quantidade sugerida", 2.5f), n("Prazo (dias)", 1.5f));
                for (var r : relatorios.reposicaoSugerida(null)) {
                    linhas.add(java.util.Arrays.asList(r.produtoNome(), qtd(r.saldoAtual(), r.unidadeMedida().name()), qtd(r.quantidadeSugerida(), r.unidadeMedida().name()), Integer.toString(r.diasReposicao())));
                    totais.quantidade("Quantidade sugerida total", r.quantidadeSugerida(), r.unidadeMedida().name());
                }
                totais.adicionar("Produtos a repor", Integer.toString(linhas.size()));
                observacao = "Quantidades sugeridas, sem orçamento ou estimativa de preço. Prazos não são somados.";
            }
            default -> throw new BusinessException("Relatório PDF não suportado: " + tipo, HttpStatus.BAD_REQUEST);
        }
        if (List.of("estoque-atual", "proximos-vencimento", "vencidos", "produtos-abertos", "reposicao-sugerida").contains(tipo)) {
            periodo = "Posição atual em " + horario.hoje().format(DATA) + " (não é um estoque histórico)";
        }
        try { return new LayoutRelatorioPdf().gerar(new DocumentoRelatorioPdf(titulo, periodo, colunas, linhas, resumo, observacao, totais.listar())); }
        catch (IOException e) { throw new BusinessException("Falha ao gerar o PDF.", HttpStatus.INTERNAL_SERVER_ERROR); }
    }

    private static Coluna c(String titulo, float peso) { return new Coluna(titulo, peso, false); }
    private static Coluna n(String titulo, float peso) { return new Coluna(titulo, peso, true); }
}
