package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.FieldValidationException;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
public class PreparadorImagemNota {
    public BufferedImage preparar(byte[] arquivo) {
        try (var entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(arquivo))) {
            var leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) throw erro("Imagem inválida. Use JPG ou PNG.");
            var leitor = leitores.next();
            try {
                if (!leitor.getFormatName().matches("(?i)JPEG|PNG")) throw erro("Use JPG ou PNG.");
                leitor.setInput(entrada);
                int largura=leitor.getWidth(0), altura=leitor.getHeight(0);
                // A dimensão é conferida antes de descomprimir uma foto muito grande.
                if (largura<=0 || altura<=0 || (long)largura*altura>20_000_000)
                    throw erro("A imagem deve ter até 20 megapixels. Reduza a resolução da foto.");
                var imagem=orientar(leitor.read(0),orientacaoExif(arquivo));
                return imagem;
            } finally { leitor.dispose(); }
        } catch (java.io.IOException e) { throw erro("Imagem inválida ou incompleta. Tire outra foto."); }
    }

    /** Aplica a orientação gravada pelo celular e entrega tons de cinza para o OCR. */
    BufferedImage orientar(BufferedImage origem, int orientacao) {
        int largura=origem.getWidth(), altura=origem.getHeight();
        boolean troca=orientacao>=5 && orientacao<=8;
        var resultado=new BufferedImage(troca?altura:largura,troca?largura:altura,BufferedImage.TYPE_BYTE_GRAY);
        var desenho=resultado.createGraphics();
        try {
            switch (orientacao) {
                case 2 -> { desenho.translate(largura,0); desenho.scale(-1,1); }
                case 3 -> { desenho.translate(largura,altura); desenho.rotate(Math.PI); }
                case 4 -> { desenho.translate(0,altura); desenho.scale(1,-1); }
                case 5 -> desenho.transform(new java.awt.geom.AffineTransform(0,1,1,0,0,0));
                case 6 -> { desenho.translate(altura,0); desenho.rotate(Math.PI/2); }
                case 7 -> desenho.transform(new java.awt.geom.AffineTransform(0,-1,-1,0,altura,largura));
                case 8 -> { desenho.translate(0,largura); desenho.rotate(-Math.PI/2); }
                default -> { }
            }
            desenho.drawImage(origem,0,0,null);
        } finally { desenho.dispose(); origem.flush(); }
        return resultado;
    }

    /** Lê somente a tag de orientação EXIF; limites impedem offsets fora do arquivo. */
    int orientacaoExif(byte[] bytes) {
        if (bytes.length<4 || (bytes[0]&255)!=255 || (bytes[1]&255)!=216) return 1;
        int posicao=2;
        while(posicao+4<=bytes.length && (bytes[posicao]&255)==255) {
            int marcador=bytes[posicao+1]&255;
            if(marcador==218 || marcador==217) break;
            int tamanho=((bytes[posicao+2]&255)<<8)|(bytes[posicao+3]&255);
            int inicio=posicao+4, fim=posicao+2+tamanho;
            if(tamanho<2 || fim>bytes.length) break;
            if(marcador==225 && tamanho>=16 && bytes[inicio]=='E' && bytes[inicio+1]=='x'
                    && bytes[inicio+2]=='i' && bytes[inicio+3]=='f') {
                int tiff=inicio+6;
                var buffer=ByteBuffer.wrap(bytes).order(bytes[tiff]=='I'?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN);
                long deslocamento=Integer.toUnsignedLong(buffer.getInt(tiff+4));
                if(deslocamento>fim-tiff-2) return 1;
                int diretorio=tiff+(int)deslocamento;
                int entradas=Short.toUnsignedInt(buffer.getShort(diretorio));
                for(int i=0;i<entradas;i++) {
                    int tag=diretorio+2+i*12;
                    if(tag+12>fim) break;
                    if(Short.toUnsignedInt(buffer.getShort(tag))==274 && buffer.getShort(tag+2)==3
                            && buffer.getInt(tag+4)==1) {
                        int valor=Short.toUnsignedInt(buffer.getShort(tag+8));
                        return valor>=1 && valor<=8 ? valor : 1;
                    }
                }
            }
            posicao=fim;
        }
        return 1;
    }
    private FieldValidationException erro(String mensagem) { return new FieldValidationException("arquivos",mensagem); }
}
