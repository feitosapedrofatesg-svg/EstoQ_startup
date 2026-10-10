package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.FieldValidationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class LeitorXmlNota {
    public DadosNota ler(byte[] arquivo) {
        try {
            var fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            // O XML da nota não pode buscar arquivos locais nem recursos externos.
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrica.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var leitor = fabrica.newDocumentBuilder();
            leitor.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            var documento = leitor.parse(new ByteArrayInputStream(arquivo));
            var notas = documento.getElementsByTagNameNS("http://www.portalfiscal.inf.br/nfe", "infNFe");
            if (notas.getLength()!=1) throw erro("Envie um XML com uma única NF-e.");
            var nota = (Element)notas.item(0);
            var ide = elemento(nota, "ide");
            if (!texto(ide, "mod").equals("55") || !texto(ide, "finNFe").equals("1") || !texto(ide, "tpNF").equals("1"))
                throw erro("A importação aceita NF-e de venda normal de mercadorias.");
            var emitente = elemento(nota, "emit");
            var itens = new ArrayList<DadosNota.ItemLido>();
            var detalhes = nota.getElementsByTagNameNS("*", "det");
            for (int i=0; i<detalhes.getLength(); i++) {
                var detalhe = (Element)detalhes.item(i);
                var produto = elemento(detalhe, "prod");
                var validades = produto.getElementsByTagNameNS("*", "dVal");
                var datas = new java.util.HashSet<String>();
                for(int j=0;j<validades.getLength();j++) datas.add(validades.item(j).getTextContent().strip());
                if(datas.size()>1) throw erro("Um produto tem lotes com validades diferentes. Registre esses lotes pela entrada manual.");
                var validade = texto(produto, "dVal");
                itens.add(new DadosNota.ItemLido(Integer.parseInt(detalhe.getAttribute("nItem")), texto(produto,"cProd"),
                        texto(produto,"xProd"), texto(produto,"cEAN"), texto(produto,"uCom"),
                        numero(produto,"qCom"), numero(produto,"vProd"), numero(produto,"vDesc"),
                        validade.isBlank() ? null : LocalDate.parse(validade), false));
            }
            var totais = elemento(nota,"ICMSTot");
            var somaProdutos = itens.stream().map(DadosNota.ItemLido::valorBruto).reduce(BigDecimal.ZERO,BigDecimal::add);
            if (somaProdutos.compareTo(numero(totais,"vProd"))!=0)
                throw erro("Os produtos não fecham o total informado no XML.");
            var data = texto(ide, "dhEmi");
            if (data.isBlank()) data = texto(ide,"dEmi");
            return new DadosNota(nota.getAttribute("Id").replaceFirst("^NFe", ""), texto(emitente,"xNome"),
                    texto(emitente,"CNPJ"), texto(ide,"nNF"), texto(ide,"serie"), LocalDate.parse(data.substring(0,10)),
                    numero(totais,"vNF"), itens, List.of());
        } catch (FieldValidationException e) { throw e; }
        catch (Exception e) { throw erro("XML inválido ou incompleto. Envie o arquivo original da NF-e."); }
    }
    private Element elemento(Element origem, String nome) {
        var lista = origem.getElementsByTagNameNS("*", nome);
        if (lista.getLength()==0) throw erro("Campo ausente no XML: " + nome + ".");
        return (Element)lista.item(0);
    }
    private String texto(Element origem, String nome) {
        var lista = origem.getElementsByTagNameNS("*", nome);
        return lista.getLength()==0 ? "" : lista.item(0).getTextContent().strip();
    }
    private BigDecimal numero(Element origem, String nome) {
        var valor = texto(origem,nome);
        return valor.isBlank() ? BigDecimal.ZERO : new BigDecimal(valor);
    }
    private FieldValidationException erro(String mensagem) { return new FieldValidationException("arquivos", mensagem); }
}
