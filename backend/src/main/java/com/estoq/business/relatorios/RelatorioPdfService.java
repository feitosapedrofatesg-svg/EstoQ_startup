package com.estoq.business.relatorios;

import com.estoq.core.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RelatorioPdfService {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGEM = 50f;
    private static final float TOPO = 790f;
    private static final float LINHA = 14f;
    private static final float MIN_Y = 40f;

    private final RelatorioService relatorios;

    public byte[] gerar(String tipo, LocalDate inicio, LocalDate fim, long dias, BigDecimal receitaBase) {
        var periodoInicio = inicio == null ? LocalDate.now().minusDays(30) : inicio;
        var periodoFim = fim == null ? LocalDate.now() : fim;
        var fimFmt = periodoFim.format(DF);
        var inicioFmt = periodoInicio.format(DF);
        var linhas = new ArrayList<String>();
        var titulo = "EstoQ - Relatório " + switch (tipo) {
            case "estoque-atual" -> "Estoque Atual";
            case "proximos-vencimento" -> "Próximos Vencimentos";
            case "vencidos" -> "Vencidos";
            case "produtos-abertos" -> "Produtos Abertos";
            case "desperdicio" -> "Desperdício";
            case "consumo-medio" -> "Consumo Médio";
            case "cmv" -> "CMV";
            case "cmv-mensal" -> "CMV Mensal";
            case "reposicao-sugerida" -> "Reposição Sugerida";
            default -> throw new BusinessException("Relatório PDF não suportado: " + tipo, HttpStatus.BAD_REQUEST);
        };
        switch (tipo) {
            case "estoque-atual" -> {
                linhas.add(cabecalho("Produto", "Categoria", "Saldo", "Valor (R$)"));
                for (var l : relatorios.estoqueAtual()) {
                    linhas.add(linha(l.produtoNome(), l.categoriaNome(), l.saldoAtual().toPlainString(), l.valorEstoque().toPlainString()));
                }
            }
            case "proximos-vencimento" -> {
                linhas.add(cabecalho("Produto", "Lote", "Validade", "Dias", "Saldo"));
                for (var l : relatorios.proximosVencimento()) {
                    linhas.add(linha(l.produtoNome(), l.codigo(),
                            l.dataValidade() == null ? "-" : l.dataValidade().format(DF),
                            l.diasParaVencimento() == null ? "-" : l.diasParaVencimento().toString(),
                            l.quantidadeAtual().toPlainString()));
                }
            }
            case "vencidos" -> {
                linhas.add(cabecalho("Produto", "Lote", "Validade", "Saldo", "Valor (R$)"));
                for (var l : relatorios.vencidos()) {
                    linhas.add(linha(l.produtoNome(), l.codigo(),
                            l.dataValidade() == null ? "-" : l.dataValidade().format(DF),
                            l.quantidadeAtual().toPlainString(), money(l.quantidadeAtual().multiply(l.precoUnitario()))));
                }
            }
            case "produtos-abertos" -> {
                linhas.add(cabecalho("Produto", "Lote", "Aberto em", "Aberta", "Restante"));
                for (var p : relatorios.produtosAbertos()) {
                    linhas.add(linha(p.produtoNome(), p.loteCodigo(),
                            p.dataAbertura() == null ? "-" : p.dataAbertura().toLocalDate().format(DF),
                            p.quantidadeAberta().toPlainString(), p.quantidadeRestante().toPlainString()));
                }
            }
            case "desperdicio" -> {
                linhas.add(cabecalho("Quando", "Produto", "Motivo", "Qtd", "Prejuízo (R$)"));
                var de = periodoInicio.atStartOfDay();
                var ate = periodoFim.atStartOfDay().plusDays(1);
                for (var d : relatorios.desperdicio(de, ate)) {
                    linhas.add(linha(d.dataHora().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                            d.produtoNome(), d.descricaoMotivo(), d.quantidade().toPlainString(), money(d.valorPrejuizo())));
                }
            }
            case "consumo-medio" -> {
                linhas.add(cabecalho("Produto", "Total", "Dias", "Média/dia"));
                var janelas = Math.max(1, dias);
                var de = periodoFim.atStartOfDay().plusDays(1).minusDays(janelas);
                var ate = periodoFim.atStartOfDay().plusDays(1);
                for (var c : relatorios.consumoMedio(de, ate)) {
                    linhas.add(linha(c.produtoNome(), c.totalConsumidoPeriodo().toPlainString(),
                            Long.toString(c.diasPeriodo()), c.consumoMedioDiario().toPlainString()));
                }
            }
            case "cmv" -> {
                var de = periodoInicio.atStartOfDay();
                var ate = periodoFim.atStartOfDay().plusDays(1);
                var cmv = relatorios.calcularCmv(de, ate, receitaBase);
                linhas.add("Estoque inicial:  R$ " + money(cmv.valorEstoqueInicial()));
                linhas.add("Compras:          R$ " + money(cmv.valorCompras()));
                linhas.add("Estoque final:    R$ " + money(cmv.valorEstoqueFinal()));
                linhas.add("CMV:              R$ " + money(cmv.cmv()));
                linhas.add("Consumo:          R$ " + money(cmv.valorConsumoRegistrado()));
                linhas.add("Desperdício:      R$ " + money(cmv.valorDesperdicio()));
                linhas.add("Perdas ocultas:   R$ " + money(cmv.valorPerdasNaoExplicadas()));
                if (cmv.receitaBase() != null) {
                    linhas.add("Receita base:     R$ " + money(cmv.receitaBase()));
                    linhas.add("CMV %:            " + cmv.cmvPercentual() + (cmv.percentualIdeal() != null ? " (ideal " + cmv.percentualIdeal() + ")" : ""));
                }
            }
            case "cmv-mensal" -> {
                linhas.add(cabecalho("Mês", "CMV (R$)"));
                var de = periodoInicio.atStartOfDay();
                var ate = periodoFim.atStartOfDay().plusDays(1);
                for (var m : relatorios.cmvPorMes(de, ate)) {
                    linhas.add(linha(m.periodo().toString(), money(m.cmv())));
                }
            }
            case "reposicao-sugerida" -> {
                linhas.add(cabecalho("Produto", "Saldo", "Sugerido", "Dias"));
                for (var r : relatorios.reposicaoSugerida(null)) {
                    linhas.add(linha(r.produtoNome(), r.saldoAtual().toPlainString(),
                            r.quantidadeSugerida().toPlainString(), Integer.toString(r.diasReposicao())));
                }
            }
            default -> {
            }
        }
        try {
            var fontTitulo = new PDType1Font(FontName.HELVETICA_BOLD);
            var fontTexto = new PDType1Font(FontName.HELVETICA);
            var pdf = new ByteArrayOutputStream();
            try (PDDocument doc = new PDDocument()) {
                var y = TOPO;
                var page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                var cs = new PDPageContentStream(doc, page);
                cs.beginText();
                cs.setFont(fontTitulo, 16);
                cs.newLineAtOffset(MARGEM, y);
                cs.showText(titulo);
                cs.endText();
                y -= LINHA;
                cs.beginText();
                cs.setFont(fontTexto, 9);
                cs.newLineAtOffset(MARGEM, y);
                cs.showText("Período: " + inicioFmt + " a " + fimFmt);
                cs.endText();
                y -= LINHA * 2;
                cs.setFont(fontTexto, 8);
                for (var texto : linhas) {
                    if (y < MIN_Y) {
                        cs.close();
                        page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        y = TOPO;
                    }
                    cs.beginText();
                    cs.newLineAtOffset(MARGEM, y);
                    cs.showText(truncar(texto));
                    cs.endText();
                    y -= LINHA;
                }
                cs.close();
                doc.save(pdf);
            }
            return pdf.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Falha ao gerar o PDF.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static String cabecalho(String... colunas) {
        return String.join(" | ", colunas);
    }

    private static String linha(String... valores) {
        return String.join(" | ", valores);
    }

    private static String truncar(String texto) {
        return texto.length() > 105 ? texto.substring(0, 102) + "..." : texto;
    }

    private static String money(BigDecimal valor) {
        return valor == null ? "0.00" : valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}