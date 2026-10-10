package com.estoq.business.notasFiscais;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class LeitorTsvNota {
    private record Palavra(int x, int centroY, int altura, String texto) { }
    private static class Linha {
        final int centroY;
        final List<Palavra> palavras=new ArrayList<>();
        Linha(int centroY) { this.centroY=centroY; }
    }
    public boolean temBaixaConfianca(String tsv) {
        for (var linha : tsv.split("\\R")) {
            var campos=linha.split("\t",12);
            if(campos.length==12 && campos[0].equals("5") && !campos[11].isBlank()
                    && Double.parseDouble(campos[10])<70) return true;
        }
        return false;
    }
    public String texto(String tsv) {
        var palavras=new ArrayList<Palavra>();
        for(var linha : tsv.split("\\R")) {
            var campos=linha.split("\t",12);
            if(campos.length!=12 || !campos[0].equals("5") || campos[11].isBlank()) continue;
            int altura=Integer.parseInt(campos[9]);
            palavras.add(new Palavra(Integer.parseInt(campos[6]),Integer.parseInt(campos[7])+altura/2,altura,campos[11].strip()));
        }
        palavras.sort(Comparator.comparingInt(Palavra::centroY).thenComparingInt(Palavra::x));
        var linhas=new ArrayList<Linha>();
        for(var palavra : palavras) {
            // Colunas podem sair em blocos diferentes no OCR; a posição vertical recompõe a linha.
            var linha=linhas.isEmpty()?null:linhas.getLast();
            if(linha==null || Math.abs(linha.centroY-palavra.centroY())>Math.max(5,palavra.altura()/2)) {
                linha=new Linha(palavra.centroY()); linhas.add(linha);
            }
            linha.palavras.add(palavra);
        }
        var resultado=new StringBuilder();
        for(var linha : linhas) {
            linha.palavras.sort(Comparator.comparingInt(Palavra::x));
            resultado.append(String.join(" ",linha.palavras.stream().map(Palavra::texto).toList())).append('\n');
        }
        return resultado.toString();
    }
}
