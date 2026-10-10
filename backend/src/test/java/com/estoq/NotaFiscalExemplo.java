package com.estoq;

/** Documentos fictícios: nenhuma identificação dos fornecedores reais vai para o Git. */
final class NotaFiscalExemplo {
    static final String CHAVE = "52260912345678000195550010000000011000000010";
    static String xml(String chave, String descricao, String codigo, String unidade, String quantidade, String valor) {
        return """
            <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe"><NFe><infNFe Id="NFe%s">
            <ide><mod>55</mod><finNFe>1</finNFe><tpNF>1</tpNF><nNF>1</nNF><serie>1</serie><dhEmi>2026-09-02T10:00:00-03:00</dhEmi></ide>
            <emit><CNPJ>12345678000195</CNPJ><xNome>Fornecedor de Teste</xNome></emit>
            <det nItem="1"><prod><cProd>%s</cProd><xProd>%s</xProd><uCom>%s</uCom><qCom>%s</qCom><vProd>%s</vProd><vDesc>0.00</vDesc></prod></det>
            <total><ICMSTot><vNF>%s</vNF></ICMSTot></total>
            </infNFe></NFe></nfeProc>
            """.formatted(chave,codigo,descricao,unidade,quantidade,valor,valor);
    }
}
