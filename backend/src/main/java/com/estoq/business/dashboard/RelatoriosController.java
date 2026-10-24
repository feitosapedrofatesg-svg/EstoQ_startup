package com.estoq.business.dashboard;

import com.estoq.core.exceptions.FieldValidationException;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.business.lotes.LoteDTO;
import com.estoq.business.produtos.EstoqueDTO;
import com.estoq.business.produtosAbertos.ProdutoAbertoDTO;
import com.estoq.business.relatorios.CmvMensalDTO;
import com.estoq.business.relatorios.CmvReceitaBaseDTO;
import com.estoq.business.relatorios.CmvReceitaBaseService;
import com.estoq.business.relatorios.CmvResumoDTO;
import com.estoq.business.relatorios.FaturamentoDiarioDTO;
import com.estoq.business.relatorios.FaturamentoPeriodoDTO;
import jakarta.validation.Valid;
import com.estoq.business.relatorios.ConsumoDiaSemanaDTO;
import com.estoq.business.relatorios.ConsumoMedioDTO;
import com.estoq.business.relatorios.DesperdicioAgregadoDTO;
import com.estoq.business.relatorios.DesperdicioDTO;
import com.estoq.business.relatorios.RelatorioService;
import com.estoq.business.relatorios.RelatorioPdfService;
import com.estoq.business.relatorios.ReposicaoSugeridaDTO;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RelatoriosController {

    private final HorarioOperacao horario;

    private final RelatorioService relatorios;
    private final RelatorioPdfService pdfs;
    private final DashboardService dashboard;
    private final CmvReceitaBaseService receitasCmv;

    @GetMapping("/api/relatorios/estoque-atual")
    public List<EstoqueDTO> estoqueAtual() {
        return relatorios.estoqueAtual();
    }

    @GetMapping("/api/relatorios/proximos-vencimento")
    public List<LoteDTO> proximosVencimento() {
        return relatorios.proximosVencimento();
    }

    @GetMapping("/api/relatorios/vencidos")
    public List<LoteDTO> vencidos() {
        return relatorios.vencidos();
    }

    @GetMapping("/api/relatorios/produtos-abertos")
    public List<ProdutoAbertoDTO> produtosAbertos() {
        return relatorios.produtosAbertos();
    }

    @GetMapping("/api/relatorios/desperdicio")
    public List<DesperdicioDTO> desperdicio(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.desperdicio(definirInicio(inicio), definirFim(fim));
    }

    @GetMapping("/api/relatorios/desperdicio/agregado")
    public List<DesperdicioAgregadoDTO> desperdicioAgregado(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.desperdicioAgregado(definirInicio(inicio), definirFim(fim));
    }

    @GetMapping("/api/relatorios/consumo-medio")
    public List<ConsumoMedioDTO> consumoMedio(
            @RequestParam(defaultValue = "30") long dias,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        if (dias < 1 || dias > 3660)
            throw new FieldValidationException("dias", "Informe de 1 a 3660 dias.");
        var fimAt = horario.inicioUtc((fim == null ? horario.hoje() : fim).plusDays(1));
        return relatorios.consumoMedio(horario.inicioUtc((fim == null ? horario.hoje() : fim).plusDays(1).minusDays(dias)), fimAt);
    }

    @GetMapping("/api/relatorios/consumo/dia-semana")
    public List<ConsumoDiaSemanaDTO> consumoDiaSemana(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.consumoPorDiaSemana(definirInicio(inicio), definirFim(fim));
    }

    @GetMapping("/api/relatorios/cmv")
    public CmvResumoDTO cmv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) BigDecimal receitaBase) {
        return relatorios.calcularCmv(definirInicio(inicio), definirFim(fim), receitaBase);
    }

    @GetMapping("/api/relatorios/cmv/receita-base")
    public CmvReceitaBaseDTO receitaBaseCmv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return receitasCmv.obter(inicio, fim);
    }

    @PutMapping("/api/relatorios/cmv/receita-base")
    public CmvReceitaBaseDTO salvarReceitaBaseCmv(@RequestBody CmvReceitaBaseDTO dto) {
        return receitasCmv.salvar(dto);
    }

    @GetMapping("/api/relatorios/cmv/mensal")
    public List<CmvMensalDTO> cmvMensal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.cmvPorMes(definirInicio(inicio), definirFim(fim));
    }

    @GetMapping("/api/relatorios/faturamento-diario")
    public FaturamentoPeriodoDTO faturamentoDiario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return receitasCmv.faturamento(inicio, fim);
    }

    @PutMapping("/api/relatorios/faturamento-diario")
    public FaturamentoDiarioDTO salvarFaturamentoDiario(@Valid @RequestBody FaturamentoDiarioDTO dto) {
        return receitasCmv.salvarDia(dto);
    }

    @GetMapping("/api/relatorios/reposicao-sugerida")
    public List<ReposicaoSugeridaDTO> reposicaoSugerida(
            @RequestParam(required = false) Integer dias) {
        return relatorios.reposicaoSugerida(dias);
    }

    @GetMapping(value = "/api/relatorios/pdf/{tipo}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            @PathVariable String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "30") long dias,
            @RequestParam(required = false) BigDecimal receitaBase) {
        var bytes = pdfs.gerar(tipo, inicio, fim, dias, receitaBase);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=relatorio-" + tipo + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @GetMapping("/api/relatorios/exportar")
    public void exportar(
            @RequestParam String tipo,
            @RequestParam String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "30") long dias,
            @RequestParam(required = false) BigDecimal receitaBase,
            HttpServletResponse response) throws IOException {
        if (!"pdf".equals(formato) && !"csv".equals(formato) && !"xls".equals(formato) && !"xlsx".equals(formato)) {
            throw new FieldValidationException("formato", "Formato deve ser pdf, csv ou xls.");
        }
        String nomeBase = "estoQ-" + tipo + "-" + (inicio == null ? horario.hoje() : inicio) + "-a-" + (fim == null ? horario.hoje() : fim);
        String extensao = ("xls".equals(formato) || "xlsx".equals(formato)) ? "xls" : formato;
        String nomeArquivo = URLEncoder.encode(nomeBase + "." + extensao, StandardCharsets.UTF_8).replace("+", "%20");
        if ("pdf".equals(formato)) {
            byte[] bytes = pdfs.gerar(tipo, inicio, fim, dias, receitaBase);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + nomeArquivo);
            response.getOutputStream().write(bytes);
            return;
        }
        if ("csv".equals(formato)) {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + nomeArquivo);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            try (PrintWriter out = response.getWriter()) {
                out.write('\ufeff');
                gerarCsv(tipo, inicio, fim, dias, out);
            }
            return;
        }
        response.setContentType("application/vnd.ms-excel; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + nomeArquivo);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter out = response.getWriter()) {
            out.write('\ufeff');
            gerarXls(tipo, inicio, fim, dias, receitaBase, out);
        }
    }

    private void gerarCsv(String tipo, LocalDate inicio, LocalDate fim, long dias, PrintWriter out) {
        var periodoInicio = inicio == null ? horario.hoje().minusDays(30) : inicio;
        var periodoFim = fim == null ? horario.hoje() : fim;
        var de = horario.inicioUtc(periodoInicio);
        var ate = horario.inicioUtc(periodoFim.plusDays(1));
        DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter DATAHORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        String titulo = switch (tipo) {
            case "estoque-atual" -> "Estoque atual";
            case "proximos-vencimento" -> "Lotes próximos do vencimento";
            case "vencidos" -> "Lotes vencidos";
            case "produtos-abertos" -> "Embalagens abertas";
            case "desperdicio" -> "Desperdício no período";
            case "consumo-medio" -> "Consumo médio";
            case "consumo-dia-semana" -> "Consumo por dia da semana";
            case "faturamento" -> "Faturamento no período";
            case "cmv" -> "Custo da mercadoria vendida (CMV)";
            case "cmv-mensal" -> "CMV mês a mês";
            case "reposicao-sugerida" -> "Reposição sugerida";
            default -> "Relatório";
        };
        boolean usaPeriodo = List.of("desperdicio", "consumo-medio", "consumo-dia-semana", "faturamento", "cmv", "cmv-mensal").contains(tipo);
        out.println("EstoQ");
        out.println(titulo);
        out.println(usaPeriodo ? "Período;" + periodoInicio.format(DATA) + " a " + periodoFim.format(DATA) : "Posição;" + periodoFim.format(DATA));
        out.println("Gerado em;" + java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (UTC-3)");
        out.println();

        switch (tipo) {
            case "estoque-atual" -> {
                out.println("Produto;Categoria;Unidade;Saldo;Valor em estoque");
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.estoqueAtual()) {
                    valorTotal = valorTotal.add(l.valorEstoque() == null ? BigDecimal.ZERO : l.valorEstoque());
                    out.printf("%s;%s;%s;%s;%s%n", l.produtoNome(), l.categoriaNome() == null ? "" : l.categoriaNome(), l.unidadeMedida(), l.saldoAtual() == null ? "" : l.saldoAtual().toString().replace(".", ","), l.valorEstoque() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", l.valorEstoque()).replace(".", ","));
                }
                out.println();
                out.printf("TOTAL EM ESTOQUE;;;%s;%s%n", "", String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", valorTotal).replace(".", ","));
            }
            case "proximos-vencimento" -> {
                out.println("Produto;Lote;Validade;Dias restantes;Quantidade;Unidade;Preço unitário;Valor do saldo");
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.proximosVencimento()) {
                    var valor = (l.quantidadeAtual() == null ? BigDecimal.ZERO : l.quantidadeAtual()).multiply(l.precoUnitario() == null ? BigDecimal.ZERO : l.precoUnitario());
                    valorTotal = valorTotal.add(valor);
                    out.printf("%s;%s;%s;%s;%s;%s;%s;%s%n", l.produtoNome(), l.codigo(), l.dataValidade() == null ? "" : l.dataValidade().format(DATA), l.diasParaVencimento() == null ? "" : l.diasParaVencimento().toString(), l.quantidadeAtual() == null ? "" : l.quantidadeAtual().toString().replace(".", ","), l.unidadeMedida(), l.precoUnitario() == null ? "" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", l.precoUnitario()).replace(".", ","), String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", valor).replace(".", ","));
                }
                out.printf(";;;;;;;%s%n", String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", valorTotal).replace(".", ","));
            }
            case "vencidos" -> {
                out.println("Produto;Lote;Validade;Quantidade;Unidade;Preço unitário;Valor do saldo");
                var valorTotal = BigDecimal.ZERO;
                for (var l : relatorios.vencidos()) {
                    var valor = (l.quantidadeAtual() == null ? BigDecimal.ZERO : l.quantidadeAtual()).multiply(l.precoUnitario() == null ? BigDecimal.ZERO : l.precoUnitario());
                    valorTotal = valorTotal.add(valor);
                    out.printf("%s;%s;%s;%s;%s;%s;%s%n", l.produtoNome(), l.codigo(), l.dataValidade() == null ? "" : l.dataValidade().format(DATA), l.quantidadeAtual() == null ? "" : l.quantidadeAtual().toString().replace(".", ","), l.unidadeMedida(), l.precoUnitario() == null ? "" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", l.precoUnitario()).replace(".", ","), String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", valor).replace(".", ","));
                }
                out.printf(";;;;;;%s%n", String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", valorTotal).replace(".", ","));
            }
            case "produtos-abertos" -> {
                out.println("Produto;Lote;Abertura;Quantidade aberta;Unidade;Quantidade restante");
                for (var p : relatorios.produtosAbertos()) {
                    out.printf("%s;%s;%s;%s;%s;%s%n", p.produtoNome(), p.loteCodigo(), p.dataAbertura() == null ? "" : horario.diaLocal(p.dataAbertura()).format(DATA), p.quantidadeAberta() == null ? "" : p.quantidadeAberta().toString().replace(".", ","), p.unidadeMedida(), p.quantidadeRestante() == null ? "" : p.quantidadeRestante().toString().replace(".", ","));
                }
            }
            case "desperdicio" -> {
                out.println("Data e hora;Produto;Lote;Motivo;Descrição;Quantidade;Unidade;Prejuízo");
                var valorTotal = BigDecimal.ZERO;
                for (var d : relatorios.desperdicio(de, ate)) {
                    valorTotal = valorTotal.add(d.valorPrejuizo() == null ? BigDecimal.ZERO : d.valorPrejuizo());
                    String motivo = d.motivo() == null ? "" : d.motivo().name().replace('_', ' ');
                    out.printf("%s;%s;%s;%s;%s;%s;%s;%s%n", d.dataHora().atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/Sao_Paulo")).format(DATAHORA), d.produtoNome(), d.loteCodigo() == null ? "" : d.loteCodigo(), motivo, d.descricaoMotivo() == null ? "" : d.descricaoMotivo(), d.quantidade() == null ? "" : d.quantidade().toString().replace(".", ","), d.unidadeMedida() == null ? "" : d.unidadeMedida().toString(), d.valorPrejuizo() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", d.valorPrejuizo()).replace(".", ","));
                }
                out.printf(";;;;;;;%s%n", String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", valorTotal).replace(".", ","));
            }
            case "consumo-medio" -> {
                var inicioConsumo = horario.inicioUtc(periodoFim.plusDays(1).minusDays(dias));
                out.println("Produto;Unidade;Total consumido no período;Dias;Média por dia");
                for (var c : relatorios.consumoMedio(inicioConsumo, ate)) {
                    out.printf("%s;%s;%s;%s;%s%n", c.produtoNome(), c.unidadeMedida(), c.totalConsumidoPeriodo() == null ? "" : c.totalConsumidoPeriodo().toString().replace(".", ","), c.diasPeriodo(), c.consumoMedioDiario() == null ? "" : c.consumoMedioDiario().toString().replace(".", ","));
                }
            }
            case "consumo-dia-semana" -> {
                out.println("Dia da semana;Custo total;Custo médio por ocorrência");
                var valorTotal = BigDecimal.ZERO;
                for (var dia : relatorios.consumoPorDiaSemana(de, ate)) {
                    valorTotal = valorTotal.add(dia.valorTotal() == null ? BigDecimal.ZERO : dia.valorTotal());
                    out.printf("%s;%s;%s%n", dia.diaSemana().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("pt-BR")), dia.valorTotal() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", dia.valorTotal()).replace(".", ","), dia.valorMedio() == null ? "" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", dia.valorMedio()).replace(".", ","));
                }
                out.printf("%s;%s;%s%n", "TOTAL", String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", valorTotal).replace(".", ","), "");
            }
            case "faturamento" -> {
                out.println("Data;Faturamento;Situação");
                var faturamento = relatorios.faturamento(periodoInicio, periodoFim);
                for (var dia : faturamento.dias()) {
                    out.printf("%s;%s;%s%n", dia.data() == null ? "" : dia.data().format(DATA), dia.valor() == null ? "" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", dia.valor()).replace(".", ","), dia.valor() == null ? "Pendente" : "Informado");
                }
                out.printf("%s;%s;%s%n", "TOTAL INFORMADO", faturamento.totalInformado() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", faturamento.totalInformado()).replace(".", ","), "");
            }
            case "cmv" -> {
                out.println("Composição;Valor");
                var cmv = relatorios.calcularCmv(de, ate, null);
                out.printf("%s;%s%n", "Estoque no início do período", cmv.valorEstoqueInicial() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", cmv.valorEstoqueInicial()).replace(".", ","));
                out.printf("%s;%s%n", "+ Compras no período", cmv.valorCompras() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", cmv.valorCompras()).replace(".", ","));
                out.printf("%s;%s%n", "- Estoque no fim do período", cmv.valorEstoqueFinal() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", cmv.valorEstoqueFinal()).replace(".", ","));
                out.printf("%s;%s%n", "CMV do período", cmv.cmv() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", cmv.cmv()).replace(".", ","));
                out.printf("%s;%s%n", "Consumo registrado", cmv.valorConsumoRegistrado() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", cmv.valorConsumoRegistrado()).replace(".", ","));
                out.printf("%s;%s%n", "Desperdício", cmv.valorDesperdicio() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", cmv.valorDesperdicio()).replace(".", ","));
                out.printf("%s;%s%n", "Perdas não explicadas", cmv.valorPerdasNaoExplicadas() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", cmv.valorPerdasNaoExplicadas()).replace(".", ","));
                out.printf("%s;%s%%%n", "Percentual de CMV", cmv.cmvPercentual() == null ? "" : cmv.cmvPercentual().toString().replace(".", ","));
                out.printf("%s;%s%%%n", "Percentual de desperdício sobre o CMV", cmv.percentualDesperdicioSobreCmv() == null ? "" : cmv.percentualDesperdicioSobreCmv().toString().replace(".", ","));
            }
            case "cmv-mensal" -> {
                out.println("Mês;CMV;Compras;Desperdício");
                var cmvTotal = BigDecimal.ZERO;
                var comprasTotal = BigDecimal.ZERO;
                var desperdicioTotal = BigDecimal.ZERO;
                for (var m : relatorios.cmvPorMes(de, ate)) {
                    cmvTotal = cmvTotal.add(m.cmv() == null ? BigDecimal.ZERO : m.cmv());
                    comprasTotal = comprasTotal.add(m.valorCompras() == null ? BigDecimal.ZERO : m.valorCompras());
                    desperdicioTotal = desperdicioTotal.add(m.valorDesperdicio() == null ? BigDecimal.ZERO : m.valorDesperdicio());
                    out.printf("%s;%s;%s;%s%n", m.periodo() == null ? "" : m.periodo().format(DateTimeFormatter.ofPattern("MM/yyyy")), m.cmv() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", m.cmv()).replace(".", ","), m.valorCompras() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", m.valorCompras()).replace(".", ","), m.valorDesperdicio() == null ? "0,00" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", m.valorDesperdicio()).replace(".", ","));
                }
                out.printf("%s;%s;%s;%s%n", "TOTAL", String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", cmvTotal).replace(".", ","), String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", comprasTotal).replace(".", ","), String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", desperdicioTotal).replace(".", ","));
            }
            case "reposicao-sugerida" -> {
                out.println("Produto;Categoria;Unidade;Saldo atual;Estoque mínimo;Consumo médio diário;Quantidade sugerida;Dias para reposição");
                for (var r : relatorios.reposicaoSugerida(null)) {
                    out.printf("%s;%s;%s;%s;%s;%s;%s;%s%n", r.produtoNome(), r.categoriaNome() == null ? "" : r.categoriaNome(), r.unidadeMedida(), r.saldoAtual() == null ? "" : r.saldoAtual().toString().replace(".", ","), r.estoqueMinimo() == null ? "" : r.estoqueMinimo().toString().replace(".", ","), r.consumoMedioDiario() == null ? "" : r.consumoMedioDiario().toString().replace(".", ","), r.quantidadeSugerida() == null ? "" : r.quantidadeSugerida().toString().replace(".", ","), r.diasReposicao());
                }
            }
            default -> throw new com.estoq.core.exceptions.BusinessException("Relatório não suportado: " + tipo, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/api/dashboard/resumo")
    public DashboardResumoDTO resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) BigDecimal receitaBase) {
        var diaFinal = fim == null ? horario.hoje() : fim;
        var diaInicial = inicio == null ? diaFinal.minusDays(29) : inicio;
        return dashboard.resumo(horario.inicioUtc(diaInicial), horario.inicioUtc(diaFinal.plusDays(1)), receitaBase);
    }

    private LocalDateTime definirInicio(LocalDate inicio) {
        if (inicio != null) {
            return horario.inicioUtc(inicio);
        }
        return horario.inicioUtc(horario.hoje().minusDays(30));
    }

    private LocalDateTime definirFim(LocalDate fim) {
        return horario.inicioUtc((fim == null ? horario.hoje() : fim).plusDays(1));
    }

    private void gerarXls(String tipo, LocalDate inicio, LocalDate fim, long dias, BigDecimal receitaBase, PrintWriter out) {
        var periodoInicio = inicio == null ? horario.hoje().minusDays(30) : inicio;
        var periodoFim = fim == null ? horario.hoje() : fim;
        var de = horario.inicioUtc(periodoInicio);
        var ate = horario.inicioUtc(periodoFim.plusDays(1));
        DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter DATAHORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        String titulo = switch (tipo) {
            case "estoque-atual" -> "Estoque atual";
            case "proximos-vencimento" -> "Lotes próximos do vencimento";
            case "vencidos" -> "Lotes vencidos";
            case "produtos-abertos" -> "Embalagens abertas";
            case "desperdicio" -> "Desperdício no período";
            case "consumo-medio" -> "Consumo médio";
            case "consumo-dia-semana" -> "Consumo por dia da semana";
            case "faturamento" -> "Faturamento no período";
            case "cmv" -> "Custo da mercadoria vendida (CMV)";
            case "cmv-mensal" -> "CMV mês a mês";
            case "reposicao-sugerida" -> "Reposição sugerida";
            default -> "Relatório";
        };
        boolean usaPeriodo = List.of("desperdicio", "consumo-medio", "consumo-dia-semana", "faturamento", "cmv", "cmv-mensal").contains(tipo);

        out.println("<html xmlns:x=\"urn:schemas-microsoft-com:office:excel\"><head><meta charset=\"UTF-8\"><style>");
        out.println("body{font-family:Arial,sans-serif;font-size:11pt;}");
        out.println(".brand{font-size:18pt;font-weight:bold;color:#272932;}");
        out.println(".title{font-size:14pt;font-weight:bold;color:#272932;margin-top:4px;}");
        out.println(".meta{font-size:9pt;color:#52545b;}");
        out.println("table{border-collapse:collapse;margin-top:12px;}");
        out.println("th{background:#272932;color:#ffffff;font-weight:bold;text-align:left;padding:8px 10px;border:1px solid #272932;}");
        out.println("td{padding:7px 10px;border:1px solid #d6d9dc;}");
        out.println(".num{text-align:right;mso-number-format:\\\"#,##0.00\\\";}");
        out.println(".int{text-align:right;mso-number-format:\\\"0\\\";}");
        out.println(".total td{background:#f6f6f7;font-weight:bold;border-top:2px solid #272932;}");
        out.println(".alt td{background:#fafafa;}");
        out.println("</style></head><body>");
        out.println("<div class=\"brand\">EstoQ</div>");
        out.println("<div class=\"title\">" + escHtml(titulo) + "</div>");
        out.println("<div class=\"meta\">" + (usaPeriodo ? "Período: " + periodoInicio.format(DATA) + " a " + periodoFim.format(DATA) : "Posição: " + periodoFim.format(DATA)) + "</div>");
        out.println("<div class=\"meta\">Gerado em: " + java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (UTC-3)</div>");

        if ("estoque-atual".equals(tipo)) {
            out.println("<table><thead><tr><th>Produto</th><th>Categoria</th><th>Unidade</th><th>Saldo</th><th>Valor em estoque</th></tr></thead><tbody>");
            var valorTotal = BigDecimal.ZERO;
            int i = 0;
            for (var l : relatorios.estoqueAtual()) {
                valorTotal = valorTotal.add(l.valorEstoque() == null ? BigDecimal.ZERO : l.valorEstoque());
                out.println("<tr class=\"" + (i++ % 2 == 1 ? "alt" : "") + "\"><td>" + escHtml(l.produtoNome()) + "</td><td>" + escHtml(l.categoriaNome()) + "</td><td>" + l.unidadeMedida() + "</td><td class=\"num\">" + numHtml(l.saldoAtual()) + "</td><td class=\"num\">" + numHtml(l.valorEstoque()) + "</td></tr>");
            }
            out.println("<tr class=\"total\"><td colspan=\"4\">TOTAL EM ESTOQUE</td><td class=\"num\">" + numHtml(valorTotal) + "</td></tr></tbody></table>");
        } else if ("proximos-vencimento".equals(tipo)) {
            out.println("<table><thead><tr><th>Produto</th><th>Lote</th><th>Validade</th><th>Dias restantes</th><th>Quantidade</th><th>Unidade</th><th>Preço unitário</th><th>Valor do saldo</th></tr></thead><tbody>");
            var valorTotal = BigDecimal.ZERO;
            int i = 0;
            for (var l : relatorios.proximosVencimento()) {
                var valor = (l.quantidadeAtual() == null ? BigDecimal.ZERO : l.quantidadeAtual()).multiply(l.precoUnitario() == null ? BigDecimal.ZERO : l.precoUnitario());
                valorTotal = valorTotal.add(valor);
                out.println("<tr class=\"" + (i++ % 2 == 1 ? "alt" : "") + "\"><td>" + escHtml(l.produtoNome()) + "</td><td>" + escHtml(l.codigo()) + "</td><td>" + (l.dataValidade() == null ? "" : l.dataValidade().format(DATA)) + "</td><td class=\"int\">" + (l.diasParaVencimento() == null ? "" : l.diasParaVencimento()) + "</td><td class=\"num\">" + numHtml(l.quantidadeAtual()) + "</td><td>" + l.unidadeMedida() + "</td><td class=\"num\">" + numHtml(l.precoUnitario()) + "</td><td class=\"num\">" + numHtml(valor) + "</td></tr>");
            }
            out.println("<tr class=\"total\"><td colspan=\"7\">TOTAL PRÓXIMOS DO VENCIMENTO</td><td class=\"num\">" + numHtml(valorTotal) + "</td></tr></tbody></table>");
        } else if ("vencidos".equals(tipo)) {
            out.println("<table><thead><tr><th>Produto</th><th>Lote</th><th>Validade</th><th>Quantidade</th><th>Unidade</th><th>Preço unitário</th><th>Valor do saldo</th></tr></thead><tbody>");
            var valorTotal = BigDecimal.ZERO;
            int i = 0;
            for (var l : relatorios.vencidos()) {
                var valor = (l.quantidadeAtual() == null ? BigDecimal.ZERO : l.quantidadeAtual()).multiply(l.precoUnitario() == null ? BigDecimal.ZERO : l.precoUnitario());
                valorTotal = valorTotal.add(valor);
                out.println("<tr class=\"" + (i++ % 2 == 1 ? "alt" : "") + "\"><td>" + escHtml(l.produtoNome()) + "</td><td>" + escHtml(l.codigo()) + "</td><td>" + (l.dataValidade() == null ? "" : l.dataValidade().format(DATA)) + "</td><td class=\"num\">" + numHtml(l.quantidadeAtual()) + "</td><td>" + l.unidadeMedida() + "</td><td class=\"num\">" + numHtml(l.precoUnitario()) + "</td><td class=\"num\">" + numHtml(valor) + "</td></tr>");
            }
            out.println("<tr class=\"total\"><td colspan=\"6\">TOTAL LOTES VENCIDOS</td><td class=\"num\">" + numHtml(valorTotal) + "</td></tr></tbody></table>");
        } else if ("produtos-abertos".equals(tipo)) {
            out.println("<table><thead><tr><th>Produto</th><th>Lote</th><th>Abertura</th><th>Quantidade aberta</th><th>Unidade</th><th>Quantidade restante</th></tr></thead><tbody>");
            int i = 0;
            for (var p : relatorios.produtosAbertos()) {
                out.println("<tr class=\"" + (i++ % 2 == 1 ? "alt" : "") + "\"><td>" + escHtml(p.produtoNome()) + "</td><td>" + escHtml(p.loteCodigo()) + "</td><td>" + (p.dataAbertura() == null ? "" : horario.diaLocal(p.dataAbertura()).format(DATA)) + "</td><td class=\"num\">" + numHtml(p.quantidadeAberta()) + "</td><td>" + p.unidadeMedida() + "</td><td class=\"num\">" + numHtml(p.quantidadeRestante()) + "</td></tr>");
            }
            out.println("<tr class=\"total\"><td colspan=\"5\">TOTAL EMBALAGENS ABERTAS</td><td class=\"int\">" + relatorios.produtosAbertos().size() + "</td></tr></tbody></table>");
        } else if ("desperdicio".equals(tipo)) {
            out.println("<table><thead><tr><th>Data e hora</th><th>Produto</th><th>Lote</th><th>Motivo</th><th>Descrição</th><th>Quantidade</th><th>Unidade</th><th>Prejuízo</th></tr></thead><tbody>");
            var valorTotal = BigDecimal.ZERO;
            int i = 0;
            for (var d : relatorios.desperdicio(de, ate)) {
                valorTotal = valorTotal.add(d.valorPrejuizo() == null ? BigDecimal.ZERO : d.valorPrejuizo());
                String motivo = d.motivo() == null ? "" : d.motivo().name().replace('_', ' ');
                out.println("<tr class=\"" + (i++ % 2 == 1 ? "alt" : "") + "\"><td>" + (d.dataHora() == null ? "" : d.dataHora().atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/Sao_Paulo")).format(DATAHORA)) + "</td><td>" + escHtml(d.produtoNome()) + "</td><td>" + escHtml(d.loteCodigo()) + "</td><td>" + escHtml(motivo) + "</td><td>" + escHtml(d.descricaoMotivo()) + "</td><td class=\"num\">" + numHtml(d.quantidade()) + "</td><td>" + (d.unidadeMedida() == null ? "" : d.unidadeMedida()) + "</td><td class=\"num\">" + numHtml(d.valorPrejuizo()) + "</td></tr>");
            }
            out.println("<tr class=\"total\"><td colspan=\"7\">PREJUÍZO TOTAL NO PERÍODO</td><td class=\"num\">" + numHtml(valorTotal) + "</td></tr></tbody></table>");
        } else if ("cmv".equals(tipo)) {
            out.println("<table><thead><tr><th>Composição e indicadores</th><th>Valor</th></tr></thead><tbody>");
            var cmv = relatorios.calcularCmv(de, ate, receitaBase);
            String[][] linhas = {
                {"Estoque no início do período", numHtml(cmv.valorEstoqueInicial())},
                {"+ Compras no período", numHtml(cmv.valorCompras())},
                {"- Estoque no fim do período", numHtml(cmv.valorEstoqueFinal())},
                {"CMV do período", numHtml(cmv.cmv())},
                {"Consumo registrado", numHtml(cmv.valorConsumoRegistrado())},
                {"Desperdício", numHtml(cmv.valorDesperdicio())},
                {"Perdas não explicadas", numHtml(cmv.valorPerdasNaoExplicadas())},
                {"Percentual de CMV", (cmv.cmvPercentual() == null ? "" : cmv.cmvPercentual().toString().replace(".", ",")) + "%"},
                {"Percentual de desperdício sobre o CMV", (cmv.percentualDesperdicioSobreCmv() == null ? "" : cmv.percentualDesperdicioSobreCmv().toString().replace(".", ",")) + "%"},
                {"Percentual ideal de CMV", (cmv.percentualIdeal() == null ? "" : cmv.percentualIdeal().toString().replace(".", ",")) + "%"},
            };
            int i = 0;
            for (String[] l : linhas) {
                out.println("<tr class=\"" + (i++ % 2 == 1 ? "alt" : "") + "\"><td>" + escHtml(l[0]) + "</td><td class=\"num\">" + l[1] + "</td></tr>");
            }
            out.println("<tr class=\"total\"><td>CMV TOTAL DO PERÍODO</td><td class=\"num\">" + numHtml(cmv.cmv()) + "</td></tr></tbody></table>");
        } else if ("cmv-mensal".equals(tipo)) {
            out.println("<table><thead><tr><th>Mês</th><th>CMV</th><th>Compras</th><th>Desperdício</th></tr></thead><tbody>");
            var cmvTotal = BigDecimal.ZERO; var comprasTotal = BigDecimal.ZERO; var desperdicioTotal = BigDecimal.ZERO;
            int i = 0;
            for (var m : relatorios.cmvPorMes(de, ate)) {
                cmvTotal = cmvTotal.add(m.cmv() == null ? BigDecimal.ZERO : m.cmv());
                comprasTotal = comprasTotal.add(m.valorCompras() == null ? BigDecimal.ZERO : m.valorCompras());
                desperdicioTotal = desperdicioTotal.add(m.valorDesperdicio() == null ? BigDecimal.ZERO : m.valorDesperdicio());
                out.println("<tr class=\"" + (i++ % 2 == 1 ? "alt" : "") + "\"><td>" + (m.periodo() == null ? "" : m.periodo().format(DateTimeFormatter.ofPattern("MM/yyyy"))) + "</td><td class=\"num\">" + numHtml(m.cmv()) + "</td><td class=\"num\">" + numHtml(m.valorCompras()) + "</td><td class=\"num\">" + numHtml(m.valorDesperdicio()) + "</td></tr>");
            }
            out.println("<tr class=\"total\"><td>TOTAL</td><td class=\"num\">" + numHtml(cmvTotal) + "</td><td class=\"num\">" + numHtml(comprasTotal) + "</td><td class=\"num\">" + numHtml(desperdicioTotal) + "</td></tr></tbody></table>");
        } else if ("reposicao-sugerida".equals(tipo)) {
            out.println("<table><thead><tr><th>Produto</th><th>Categoria</th><th>Unidade</th><th>Saldo atual</th><th>Estoque mínimo</th><th>Consumo médio diário</th><th>Quantidade sugerida</th><th>Dias para reposição</th></tr></thead><tbody>");
            int i = 0;
            for (var r : relatorios.reposicaoSugerida(null)) {
                out.println("<tr class=\"" + (i++ % 2 == 1 ? "alt" : "") + "\"><td>" + escHtml(r.produtoNome()) + "</td><td>" + escHtml(r.categoriaNome()) + "</td><td>" + r.unidadeMedida() + "</td><td class=\"num\">" + numHtml(r.saldoAtual()) + "</td><td class=\"num\">" + numHtml(r.estoqueMinimo()) + "</td><td class=\"num\">" + numHtml(r.consumoMedioDiario()) + "</td><td class=\"num\">" + numHtml(r.quantidadeSugerida()) + "</td><td class=\"int\">" + r.diasReposicao() + "</td></tr>");
            }
            out.println("</tbody></table>");
        } else {
            out.println("<table><thead><tr><th>Relatório</th><th>Informação</th></tr></thead><tbody>");
            out.println("<tr><td>Tipo</td><td>" + escHtml(tipo) + "</td></tr>");
            out.println("</tbody></table>");
        }

        out.println("<div class=\"meta\" style=\"margin-top:16px;\">Documento gerado pelo EstoQ · Todos os valores em R$ quando aplicável</div>");
        out.println("</body></html>");
    }

    private String escHtml(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String numHtml(BigDecimal v) {
        if (v == null) return "";
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", v);
    }
}
