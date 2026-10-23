package com.estoq.business.relatorios;

import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.helpers.HorarioOperacao;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
@RequiredArgsConstructor
public class RelatorioExportController {

    private final HorarioOperacao horario;
    private final RelatorioService relatorios;
    private final RelatorioPdfService pdfs;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATAHORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @GetMapping("/api/relatorios/exportar")
    public void exportar(
            @RequestParam String tipo,
            @RequestParam String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "30") long dias,
            @RequestParam(required = false) BigDecimal receitaBase,
            @RequestParam(required = false) Long produtoId,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) String motivo,
            HttpServletResponse response) throws IOException {

        if (!"pdf".equals(formato) && !"csv".equals(formato)) {
            throw new FieldValidationException("formato", "Formato deve ser pdf ou csv.");
        }

        String nomeBase = "estoq-" + tipo + "-" + (inicio == null ? horario.hoje() : inicio) + "-a-" + (fim == null ? horario.hoje() : fim);
        String nomeArquivo = URLEncoder.encode(nomeBase + "." + formato, StandardCharsets.UTF_8).replace("+", "%20");

        if ("pdf".equals(formato)) {
            byte[] bytes = pdfs.gerar(tipo, inicio, fim, dias, receitaBase);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + nomeArquivo);
            response.getOutputStream().write(bytes);
            return;
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + nomeArquivo);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter out = response.getWriter()) {
            out.write('\ufeff');
            gerarCsv(tipo, inicio, fim, dias, out);
        }
    }

    private void gerarCsv(String tipo, LocalDate inicio, LocalDate fim, long dias, PrintWriter out) {
        var periodoInicio = inicio == null ? horario.hoje().minusDays(30) : inicio;
        var periodoFim = fim == null ? horario.hoje() : fim;
        var de = horario.inicioUtc(periodoInicio);
        var ate = horario.inicioUtc(periodoFim.plusDays(1));

        switch (tipo) {
            case "estoque-atual" -> {
                out.println("Produto;Categoria;Unidade;Saldo;Valor em estoque");
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.estoqueAtual()) {
                    valorTotal = valorTotal.add(nulo(l.valorEstoque()));
                    out.printf("%s;%s;%s;%s;%s%n",
                            esc(l.produtoNome()), esc(l.categoriaNome()), l.unidadeMedida(),
                            num(l.saldoAtual()), moeda(l.valorEstoque()));
                }
                out.printf(";;;TOTAL;%s%n", moeda(valorTotal));
            }
            case "proximos-vencimento" -> {
                out.println("Produto;Lote;Validade;Dias restantes;Quantidade;Unidade;Preço unitário;Valor do saldo");
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.proximosVencimento()) {
                    var valor = nulo(l.quantidadeAtual()).multiply(nulo(l.precoUnitario()));
                    valorTotal = valorTotal.add(valor);
                    out.printf("%s;%s;%s;%s;%s;%s;%s;%s%n",
                            esc(l.produtoNome()), esc(l.codigo()), data(l.dataValidade()),
                            l.diasParaVencimento() == null ? "" : l.diasParaVencimento().toString(),
                            num(l.quantidadeAtual()), l.unidadeMedida(), moeda(l.precoUnitario()), moeda(valor));
                }
                out.printf(";;;;;;;%s%n", moeda(valorTotal));
            }
            case "vencidos" -> {
                out.println("Produto;Lote;Validade;Quantidade;Unidade;Preço unitário;Valor do saldo");
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.vencidos()) {
                    var valor = nulo(l.quantidadeAtual()).multiply(nulo(l.precoUnitario()));
                    valorTotal = valorTotal.add(valor);
                    out.printf("%s;%s;%s;%s;%s;%s;%s%n",
                            esc(l.produtoNome()), esc(l.codigo()), data(l.dataValidade()),
                            num(l.quantidadeAtual()), l.unidadeMedida(), moeda(l.precoUnitario()), moeda(valor));
                }
                out.printf(";;;;;;%s%n", moeda(valorTotal));
            }
            case "produtos-abertos" -> {
                out.println("Produto;Lote;Abertura;Quantidade aberta;Unidade;Quantidade restante");
                for (var p : relatorios.produtosAbertos()) {
                    out.printf("%s;%s;%s;%s;%s;%s%n",
                            esc(p.produtoNome()), esc(p.loteCodigo()),
                            p.dataAbertura() == null ? "" : horario.diaLocal(p.dataAbertura()).format(DATA),
                            num(p.quantidadeAberta()), p.unidadeMedida(),
                            num(p.quantidadeRestante()));
                }
            }
            case "desperdicio" -> {
                out.println("Data e hora;Produto;Lote;Motivo;Descrição;Quantidade;Unidade;Prejuízo");
                var valorTotal = BigDecimal.ZERO;
                for (var d : relatorios.desperdicio(de, ate)) {
                    valorTotal = valorTotal.add(nulo(d.valorPrejuizo()));
                    String motivo = d.motivo() == null ? "" : d.motivo().name().replace('_', ' ');
                    out.printf("%s;%s;%s;%s;%s;%s;%s;%s%n",
                            d.dataHora().atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/Sao_Paulo")).format(DATAHORA),
                            esc(d.produtoNome()), esc(d.loteCodigo()), esc(motivo), esc(d.descricaoMotivo()),
                            num(d.quantidade()), d.unidadeMedida() == null ? "" : d.unidadeMedida(), moeda(d.valorPrejuizo()));
                }
                out.printf(";;;;;;;%s%n", moeda(valorTotal));
            }
            case "consumo-medio" -> {
                var inicioConsumo = horario.inicioUtc(periodoFim.plusDays(1).minusDays(dias));
                out.println("Produto;Unidade;Total consumido no período;Dias;Média por dia");
                for (var c : relatorios.consumoMedio(inicioConsumo, ate)) {
                    out.printf("%s;%s;%s;%s;%s%n", esc(c.produtoNome()), c.unidadeMedida(),
                            num(c.totalConsumidoPeriodo()), c.diasPeriodo(), num(c.consumoMedioDiario()));
                }
            }
            case "consumo-dia-semana" -> {
                out.println("Dia da semana;Custo total;Custo médio por ocorrência");
                var valorTotal = BigDecimal.ZERO;
                for (var dia : relatorios.consumoPorDiaSemana(de, ate)) {
                    valorTotal = valorTotal.add(nulo(dia.valorTotal()));
                    out.printf("%s;%s;%s%n", dia.diaSemana().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("pt-BR")), moeda(dia.valorTotal()), moeda(dia.valorMedio()));
                }
                out.printf("%s;%s;%s%n", "TOTAL", moeda(valorTotal), "");
            }
            case "faturamento" -> {
                out.println("Data;Faturamento;Situação");
                var faturamento = relatorios.faturamento(periodoInicio, periodoFim);
                for (var dia : faturamento.dias()) {
                    out.printf("%s;%s;%s%n", data(dia.data()), dia.valor() == null ? "" : moeda(dia.valor()), dia.valor() == null ? "Pendente" : "Informado");
                }
                out.printf("%s;%s;%s%n", "TOTAL INFORMADO", moeda(faturamento.totalInformado()), "");
            }
            case "cmv" -> {
                out.println("Composição;Valor");
                var cmv = relatorios.calcularCmv(de, ate, null);
                out.printf("%s;%s%n", "Estoque no início do período", moeda(cmv.valorEstoqueInicial()));
                out.printf("%s;%s%n", "+ Compras no período", moeda(cmv.valorCompras()));
                out.printf("%s;%s%n", "- Estoque no fim do período", moeda(cmv.valorEstoqueFinal()));
                out.printf("%s;%s%n", "CMV do período", moeda(cmv.cmv()));
                out.printf("%s;%s%n", "Consumo registrado", moeda(cmv.valorConsumoRegistrado()));
                out.printf("%s;%s%n", "Desperdício", moeda(cmv.valorDesperdicio()));
                out.printf("%s;%s%n", "Perdas não explicadas", moeda(cmv.valorPerdasNaoExplicadas()));
                out.printf("%s;%s%%%n", "Percentual de CMV", num(cmv.cmvPercentual()));
                out.printf("%s;%s%%%n", "Percentual de desperdício sobre o CMV", num(cmv.percentualDesperdicioSobreCmv()));
                out.printf("%s;%s%%%n", "Percentual ideal de CMV", num(cmv.percentualIdeal()));
            }
            case "cmv-mensal" -> {
                out.println("Mês;CMV;Compras;Desperdício");
                var cmvTotal = BigDecimal.ZERO;
                var comprasTotal = BigDecimal.ZERO;
                var desperdicioTotal = BigDecimal.ZERO;
                for (var m : relatorios.cmvPorMes(de, ate)) {
                    cmvTotal = cmvTotal.add(nulo(m.cmv()));
                    comprasTotal = comprasTotal.add(nulo(m.valorCompras()));
                    desperdicioTotal = desperdicioTotal.add(nulo(m.valorDesperdicio()));
                    out.printf("%s;%s;%s;%s%n", m.periodo().format(DateTimeFormatter.ofPattern("MM/yyyy")), moeda(m.cmv()), moeda(m.valorCompras()), moeda(m.valorDesperdicio()));
                }
                out.printf("%s;%s;%s;%s%n", "TOTAL", moeda(cmvTotal), moeda(comprasTotal), moeda(desperdicioTotal));
            }
            case "reposicao-sugerida" -> {
                out.println("Produto;Categoria;Unidade;Saldo atual;Estoque mínimo;Consumo médio diário;Quantidade sugerida;Dias para reposição");
                for (var r : relatorios.reposicaoSugerida(null)) {
                    out.printf("%s;%s;%s;%s;%s;%s;%s;%s%n",
                            esc(r.produtoNome()), esc(r.categoriaNome()), r.unidadeMedida(),
                            num(r.saldoAtual()), num(r.estoqueMinimo()), num(r.consumoMedioDiario()),
                            num(r.quantidadeSugerida()), r.diasReposicao());
                }
            }
            default -> throw new BusinessException("Relatório não suportado: " + tipo, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    private String esc(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) return '"' + s + '"';
        return s;
    }

    private String num(java.math.BigDecimal v) {
        if (v == null) return "";
        return v.stripTrailingZeros().toPlainString().replace(".", ",");
    }

    private String moeda(java.math.BigDecimal v) {
        if (v == null) return "0,00";
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", nulo(v)).replace(".", ",");
    }

    private String data(java.time.LocalDate d) {
        return d == null ? "" : d.format(DATA);
    }

    private BigDecimal nulo(java.math.BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
