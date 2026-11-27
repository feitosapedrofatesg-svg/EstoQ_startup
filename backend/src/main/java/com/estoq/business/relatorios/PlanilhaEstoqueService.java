package com.estoq.business.relatorios;

import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.produtos.EstoqueDTO;
import com.estoq.core.helpers.HorarioOperacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import java.io.*;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.*;

/** Planilha XLSX real; os dados continuam vindo do serviço de estoque. */
@Service @RequiredArgsConstructor
public class PlanilhaEstoqueService {
    private final UsuarioAtual usuarioAtual;
    private final IRestauranteRepository restaurantes;
    private final HorarioOperacao horario;

    @Transactional(readOnly=true)
    public byte[] gerar(List<EstoqueDTO> linhas,String q,boolean abertos,boolean baixo,String situacao,String idioma) {
        String loja=restaurantes.findById(usuarioAtual.obter().getRestauranteId()).orElseThrow().getNome();
        try(var workbook=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet(tr("Estoque atual",idioma));
            var numero=workbook.createCellStyle(); numero.setDataFormat(workbook.createDataFormat().getFormat("#,##0.000"));
            var moeda=workbook.createCellStyle(); moeda.setDataFormat(workbook.createDataFormat().getFormat("\"R$\" #,##0.00"));
            var data=workbook.createCellStyle(); data.setDataFormat(workbook.createDataFormat().getFormat("en".equals(idioma)?"mm/dd/yyyy hh:mm":"dd/mm/yyyy hh:mm"));
            var titulo=workbook.createCellStyle(); titulo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); titulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var fonte=workbook.createFont(); fonte.setBold(true); fonte.setColor(IndexedColors.WHITE.getIndex()); titulo.setFont(fonte); titulo.setWrapText(true);
            for(var estilo:List.of(numero,moeda)) { estilo.setAlignment(HorizontalAlignment.RIGHT); estilo.setBorderBottom(BorderStyle.THIN); }
            cell(sheet.createRow(0),0,"EstoQ · "+tr("Estoque atual",idioma),titulo);
            cell(sheet.createRow(1),0,loja,null);
            var emissao=sheet.createRow(2);cell(emissao,0,tr("Emissão",idioma),null);
            var instante=horario.agoraUtc().atOffset(ZoneOffset.UTC).atZoneSameInstant(HorarioOperacao.FUSO).toLocalDateTime();
            emissao.createCell(1).setCellValue(instante);emissao.getCell(1).setCellStyle(data);
            cell(sheet.createRow(3),0,tr("Filtros",idioma)+": "+q+" · "+tr(situacao,idioma)+" · "+tr("Embalagem aberta",idioma)+": "+abertos+" · "+tr("Abaixo do mínimo",idioma)+": "+baixo,null);
            String[] nomes={"Produto","Categoria","Unidade","Saldo","Disponível","Valor em estoque","Mínimo","Médio","Máximo","Situação"};
            var cab=sheet.createRow(5);cab.setHeightInPoints(32);
            for(int c=0;c<nomes.length;c++)cell(cab,c,tr(nomes[c],idioma),titulo);
            int n=6;BigDecimal total=BigDecimal.ZERO;Map<String,BigDecimal> quantidades=new TreeMap<>();
            for(var l:linhas) {
                var estados=new ArrayList<String>();
                if(l.possuiLotesVencidos())estados.add(tr("Vencido",idioma));
                if(l.proximoVencimento())estados.add(tr("Próximo do vencimento",idioma));
                if(l.abaixoDoMinimo())estados.add(tr("Abaixo do mínimo",idioma));
                if(l.possuiItensAbertos())estados.add(tr("Embalagem aberta",idioma));
                Object[] valores={l.produtoNome(),l.categoriaNome(),l.unidadeMedida().name(),l.saldoAtual(),l.saldoDisponivelConsumo(),l.valorEstoque(),l.estoqueMinimo(),l.estoqueMedio(),l.estoqueMaximo(),String.join(" · ",estados)};
                var row=sheet.createRow(n++);
                for(int c=0;c<valores.length;c++)cell(row,c,valores[c],c==5?moeda:c>=3&&c<=8?numero:null);
                total=total.add(l.valorEstoque());quantidades.merge(l.unidadeMedida().name(),l.saldoAtual(),BigDecimal::add);
            }
            sheet.setAutoFilter(new CellRangeAddress(5,Math.max(5,n-1),0,9));
            var totalRow=sheet.createRow(n++);cell(totalRow,0,tr("Valor total em estoque",idioma),null);cell(totalRow,5,total,moeda);
            for(var unidade:quantidades.entrySet()){var row=sheet.createRow(n++);cell(row,0,tr("Saldo",idioma)+" · "+unidade.getKey(),null);cell(row,3,unidade.getValue(),numero);}
            for(int c=0;c<10;c++)sheet.setColumnWidth(c,(c==0?38:c==1?24:c==9?44:19)*256);
            for(int row:List.of(0,1,3))sheet.addMergedRegion(new CellRangeAddress(row,row,0,9));
            sheet.createFreezePane(0,6);sheet.setRepeatingRows(new CellRangeAddress(0,5,-1,-1));
            sheet.getPrintSetup().setLandscape(true);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);sheet.setFitToPage(true);
            workbook.write(bytes);return bytes.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Não foi possível gerar a planilha.",e);}
    }
    private static void cell(Row row,int indice,Object valor,CellStyle estilo) {
        var cell=row.createCell(indice);
        if(valor instanceof Number numero)cell.setCellValue(numero.doubleValue());else if(valor!=null)cell.setCellValue(valor.toString());
        if(estilo!=null)cell.setCellStyle(estilo);
    }
    private static String tr(String s,String idioma){return com.estoq.core.helpers.Idioma.texto(s,idioma);}
}
