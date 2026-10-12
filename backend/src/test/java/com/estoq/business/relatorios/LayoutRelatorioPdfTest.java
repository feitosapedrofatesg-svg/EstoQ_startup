package com.estoq.business.relatorios;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.estoq.business.relatorios.DocumentoRelatorioPdf.*;

class LayoutRelatorioPdfTest {
    @Test
    void retratoPreservaTextoExtensoMoedaEIdentificacao() throws Exception {
        String nome = "Farinha integral de trigo com identificação completa do fornecedor e embalagem para restaurante";
        var dados = new DocumentoRelatorioPdf("Estoque atual", "Posição atual em 12/10/2026",
                List.of(new Coluna("Produto", 4, false), new Coluna("Categoria", 2, false), new Coluna("Saldo", 2, true), new Coluna("Valor em estoque", 2, true)),
                List.of(List.of(nome, "Grãos e cereais", "125,375 kg", "R$ 123.456,78")), List.of(), null);
        var bytes = new LayoutRelatorioPdf().gerar(dados);
        guardar("estoque", bytes);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(pdf.getPage(0).getMediaBox().getHeight() > pdf.getPage(0).getMediaBox().getWidth());
            String texto = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertTrue(texto.contains("restaurante"));
            assertTrue(texto.contains("125,375 kg"));
            assertTrue(texto.contains("R$ 123.456,78"));
            assertTrue(texto.contains("Página 1 de 1"));
        }
    }

    @Test
    void paisagemRepeteCabecalhosSemCortarUltimaLinha() throws Exception {
        var linhas = new ArrayList<List<String>>();
        for (int i = 1; i <= 85; i++) linhas.add(List.of("12/10/2026 16:26", "Produto " + i + " com descrição longa para conferir a quebra sem perder palavras",
                "Deterioração da embalagem e ajuste negativo de balanço", "123,456", "R$ 9.876,54"));
        var dados = new DocumentoRelatorioPdf("Desperdício no período", "Período: 01/10/2026 a 12/10/2026",
                List.of(new Coluna("Data e hora", 2, false), new Coluna("Produto", 3, false), new Coluna("Motivo", 3, false), new Coluna("Quantidade", 2, true), new Coluna("Prejuízo", 2, true)),
                linhas, List.of(), "Quantidades na unidade de cadastro de cada produto.");
        var bytes = new LayoutRelatorioPdf().gerar(dados);
        guardar("desperdicio", bytes);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 2);
            assertTrue(pdf.getPage(0).getMediaBox().getWidth() > pdf.getPage(0).getMediaBox().getHeight());
            String texto = new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("Produto 85"));
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                var leitor = new PDFTextStripper(); leitor.setStartPage(i); leitor.setEndPage(i);
                String pagina = leitor.getText(pdf);
                assertTrue(pagina.contains("Prejuízo"));
                assertTrue(pagina.contains("Página " + i + " de " + pdf.getNumberOfPages()));
                assertTrue(pagina.contains("Período: 01/10/2026 a 12/10/2026"));
            }
            // Verifica os limites físicos da página, além de extrair seu texto.
            new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition texto) {
                    assertTrue(texto.getXDirAdj() >= 39);
                    assertTrue(texto.getXDirAdj() + texto.getWidthDirAdj() <= pdf.getPage(0).getMediaBox().getWidth() - 39);
                    assertTrue(texto.getYDirAdj() <= pdf.getPage(0).getMediaBox().getHeight() - 20);
                    super.processTextPosition(texto);
                }
            }.getText(pdf);
        }
    }

    @Test
    void resumoEListaVaziaNaoGeramPaginasEmBranco() throws Exception {
        var dados = new DocumentoRelatorioPdf("Custo da mercadoria vendida (CMV)", "Período: 01/10/2026 a 12/10/2026",
                List.of(new Coluna("Composição e indicadores", 6, false), new Coluna("Valor", 3, true)),
                List.of(List.of("Estoque inicial", "R$ 12.345,67"), List.of("Compras", "R$ 5.000,00"), List.of("Estoque final", "R$ 9.345,67"), List.of("Receita base informada", "R$ 25.000,00")),
                List.of(new Destaque("CMV do período", "R$ 8.000,00"), new Destaque("Percentual de CMV", "32%")), "CMV = estoque inicial + compras - estoque final.");
        var bytes = new LayoutRelatorioPdf().gerar(dados);
        guardar("cmv", bytes);
        try (var pdf = Loader.loadPDF(bytes)) { assertEquals(1, pdf.getNumberOfPages()); }
        var vazio = new DocumentoRelatorioPdf("Consumo médio", "Período: 12/10/2026 a 12/10/2026", dados.colunas(), List.of(), List.of(), null);
        try (var pdf = Loader.loadPDF(new LayoutRelatorioPdf().gerar(vazio))) {
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(pdf).contains("Nenhum registro"));
        }
    }

    private void guardar(String nome, byte[] bytes) throws Exception {
        String pasta = System.getProperty("estoq.pdf.validacao");
        if (pasta != null) { Files.createDirectories(Path.of(pasta)); Files.write(Path.of(pasta, nome + ".pdf"), bytes); }
    }
}
