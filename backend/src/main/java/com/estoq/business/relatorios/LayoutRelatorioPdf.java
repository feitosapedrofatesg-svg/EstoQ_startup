package com.estoq.business.relatorios;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Layout compartilhado dos PDFs: medidas em pontos, A4 e margens de 14 mm. */
final class LayoutRelatorioPdf {
    private static final float MARGEM = 40, FONTE = 10, ENTRELINHA = 14, RODAPE = 58;
    private static final Color TINTA = new Color(0x1d2733), LARANJA = new Color(0xef7320);
    private static final Color LINHA = new Color(0xd6d9dc), PAPEL = new Color(0xf2f3f4);
    private final PDFont normal = new PDType1Font(FontName.HELVETICA);
    private final PDFont negrito = new PDType1Font(FontName.HELVETICA_BOLD);
    private PDDocument documento;
    private PDPageContentStream desenho;
    private DocumentoRelatorioPdf dados;
    private float largura, altura, y;
    private float[] larguras;
    private String emissao;

    byte[] gerar(DocumentoRelatorioPdf dados) throws IOException {
        this.dados = dados;
        boolean paisagem = dados.colunas().size() >= 5;
        largura = paisagem ? PDRectangle.A4.getHeight() : PDRectangle.A4.getWidth();
        altura = paisagem ? PDRectangle.A4.getWidth() : PDRectangle.A4.getHeight();
        float pesos = (float) dados.colunas().stream().mapToDouble(DocumentoRelatorioPdf.Coluna::peso).sum();
        larguras = new float[dados.colunas().size()];
        for (int i = 0; i < larguras.length; i++) larguras[i] = (largura - 2 * MARGEM) * dados.colunas().get(i).peso() / pesos;
        emissao = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm ' (UTC-3)'"));
        try (var doc = new PDDocument(); var saida = new ByteArrayOutputStream()) {
            documento = doc;
            doc.getDocumentInformation().setTitle(dados.titulo());
            doc.getDocumentInformation().setAuthor("EstoQ");
            novaPagina(true);
            cabecalhoTabela();
            if (dados.linhas().isEmpty()) texto("Nenhum registro para os filtros selecionados.", MARGEM + 8, y - 22, normal, FONTE);
            for (int i = 0; i < dados.linhas().size(); i++) linhaTabela(dados.linhas().get(i), i % 2 == 1);
            if (desenho != null) desenho.close();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                try (var rodape = new PDPageContentStream(doc, doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    desenho = rodape;
                    traço(MARGEM, 42, largura - MARGEM, 42);
                    texto("EstoQ | " + dados.linhas().size() + (dados.linhas().size() == 1 ? " registro" : " registros"), MARGEM, 27, normal, 9);
                    String pagina = "Página " + (i + 1) + " de " + doc.getNumberOfPages();
                    texto(pagina, largura - MARGEM - medida(pagina, normal, 9), 27, normal, 9);
                }
            }
            desenho = null;
            doc.save(saida);
            return saida.toByteArray();
        } finally {
            if (desenho != null) desenho.close();
        }
    }

    private void novaPagina(boolean primeira) throws IOException {
        if (desenho != null) desenho.close();
        var pagina = new PDPage(new PDRectangle(largura, altura));
        documento.addPage(pagina);
        desenho = new PDPageContentStream(documento, pagina);
        y = altura - MARGEM;
        texto("EstoQ", MARGEM, y - 1, negrito, 18);
        desenho.setStrokingColor(LARANJA); desenho.setLineWidth(2);
        desenho.moveTo(MARGEM, y - 13); desenho.lineTo(largura - MARGEM, y - 13); desenho.stroke();
        y -= 36;
        for (var linha : quebrar(dados.titulo(), negrito, 16, largura - 2 * MARGEM)) {
            texto(linha, MARGEM, y, negrito, 16); y -= 20;
        }
        texto(dados.periodo(), MARGEM, y, normal, 10); y -= 16;
        texto("Emissão: " + emissao, MARGEM, y, normal, 9); y -= 23;
        if (primeira) {
            for (var destaque : dados.resumo()) {
                texto(destaque.rotulo(), MARGEM + 10, y - 17, negrito, 10);
                String valor = destaque.valor();
                texto(valor, largura - MARGEM - 10 - medida(valor, negrito, 13), y - 18, negrito, 13);
                traço(MARGEM, y - 29, largura - MARGEM, y - 29); y -= 39;
            }
            if (dados.observacao() != null) {
                y -= 8;
                for (var linha : quebrar(dados.observacao(), normal, 9, largura - 2 * MARGEM)) {
                    texto(linha, MARGEM, y, normal, 9); y -= 13;
                }
                y -= 9;
            }
        }
    }

    private void cabecalhoTabela() throws IOException {
        var linhas = new ArrayList<List<String>>();
        for (int i = 0; i < larguras.length; i++) linhas.add(quebrar(dados.colunas().get(i).titulo(), negrito, FONTE, larguras[i] - 16));
        float h = linhas.stream().mapToInt(List::size).max().orElse(1) * ENTRELINHA + 16;
        fundo(y - h, h, PAPEL);
        desenharCelulas(linhas, y, negrito, true);
        y -= h; traço(MARGEM, y, largura - MARGEM, y);
    }

    private void linhaTabela(List<String> valores, boolean sombrear) throws IOException {
        var celulas = new ArrayList<List<String>>();
        for (int i = 0; i < larguras.length; i++) celulas.add(quebrar(i < valores.size() ? valores.get(i) : "", normal, FONTE, larguras[i] - 16));
        int total = celulas.stream().mapToInt(List::size).max().orElse(1);
        float h = total * ENTRELINHA + 16;
        // Linhas normais ficam inteiras. Textos maiores que uma página continuam sem corte.
        if (y - h < RODAPE && h <= altura - MARGEM - 150 - RODAPE) { novaPagina(false); cabecalhoTabela(); }
        int offset = 0;
        while (offset < total) {
            int capacidade = Math.max(1, (int) ((y - RODAPE - 16) / ENTRELINHA));
            int quantidade = Math.min(total - offset, capacidade);
            var trecho = new ArrayList<List<String>>();
            for (var celula : celulas) trecho.add(celula.subList(Math.min(offset, celula.size()), Math.min(offset + quantidade, celula.size())));
            float bloco = quantidade * ENTRELINHA + 16;
            if (sombrear) fundo(y - bloco, bloco, new Color(0xf8f9fa));
            desenharCelulas(trecho, y, normal, true);
            y -= bloco; traço(MARGEM, y, largura - MARGEM, y);
            offset += quantidade;
            if (offset < total) { novaPagina(false); cabecalhoTabela(); }
        }
    }

    private void desenharCelulas(List<List<String>> celulas, float topo, PDFont fonte, boolean alinharNumeros) throws IOException {
        float x = MARGEM;
        for (int i = 0; i < celulas.size(); i++) {
            float linhaY = topo - 18;
            for (var linha : celulas.get(i)) {
                float inicio = alinharNumeros && dados.colunas().get(i).numerica() ? x + larguras[i] - 8 - medida(linha, fonte, FONTE) : x + 8;
                texto(linha, inicio, linhaY, fonte, FONTE); linhaY -= ENTRELINHA;
            }
            x += larguras[i];
        }
    }

    /** Quebra também identificadores sem espaços; não descarta conteúdo extenso. */
    private List<String> quebrar(String valor, PDFont fonte, float tamanho, float limite) throws IOException {
        String texto = seguro(valor, fonte);
        var resultado = new ArrayList<String>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : texto.split("\\s+")) {
            String candidato = atual.isEmpty() ? palavra : atual + " " + palavra;
            if (medida(candidato, fonte, tamanho) <= limite) { atual.setLength(0); atual.append(candidato); continue; }
            if (!atual.isEmpty()) { resultado.add(atual.toString()); atual.setLength(0); }
            for (int i = 0; i < palavra.length(); i++) {
                if (!atual.isEmpty() && medida(atual.toString() + palavra.charAt(i), fonte, tamanho) > limite) {
                    resultado.add(atual.toString()); atual.setLength(0);
                }
                atual.append(palavra.charAt(i));
            }
        }
        if (!atual.isEmpty()) resultado.add(atual.toString());
        if (resultado.isEmpty()) resultado.add("");
        return resultado;
    }

    private String seguro(String valor, PDFont fonte) {
        if (valor == null) return "-";
        var resultado = new StringBuilder();
        valor.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { resultado.append(' '); return; }
            String letra = new String(Character.toChars(cp));
            try { fonte.encode(letra); resultado.append(letra); }
            catch (IOException | IllegalArgumentException e) { resultado.append('?'); }
        });
        return resultado.toString();
    }
    private float medida(String texto, PDFont fonte, float tamanho) throws IOException { return fonte.getStringWidth(seguro(texto, fonte)) / 1000 * tamanho; }
    private void texto(String texto, float x, float y, PDFont fonte, float tamanho) throws IOException {
        desenho.setNonStrokingColor(TINTA); desenho.beginText(); desenho.setFont(fonte, tamanho);
        desenho.newLineAtOffset(x, y); desenho.showText(seguro(texto, fonte)); desenho.endText();
    }
    private void fundo(float baixo, float h, Color cor) throws IOException {
        desenho.setNonStrokingColor(cor); desenho.addRect(MARGEM, baixo, largura - 2 * MARGEM, h); desenho.fill();
    }
    private void traço(float x, float y, float fim, float yFim) throws IOException {
        desenho.setStrokingColor(LINHA); desenho.setLineWidth(0.5f); desenho.moveTo(x, y); desenho.lineTo(fim, yFim); desenho.stroke();
    }
}
