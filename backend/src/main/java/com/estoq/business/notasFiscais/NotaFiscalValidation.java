package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.FieldValidationException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;

@Component
public class NotaFiscalValidation {
    public void validarLeitura(DadosNota nota) {
        if (nota.chave()==null || !nota.chave().matches("[0-9]{44}") || !nota.chave().substring(20,22).equals("55"))
            erro("Chave de acesso inválida. Envie o documento original da NF-e.");
        if (nota.documentoFornecedor()==null || !nota.documentoFornecedor().equals(nota.chave().substring(6,20)))
            erro("O fornecedor não corresponde à chave da nota.");
        if (nota.fornecedor()==null || nota.fornecedor().isBlank() || nota.fornecedor().length()>160
                || nota.numero()==null || nota.numero().length()>20 || nota.serie()==null || nota.serie().length()>10)
            erro("Identificação da nota inválida.");
        if (nota.valorTotal()==null || nota.valorTotal().signum()<0 || nota.valorTotal().precision()>18)
            erro("Total da nota inválido.");
        if (nota.itens().isEmpty() || nota.itens().size()>1000) erro("A nota deve conter de 1 a 1000 itens.");
        var numeros = new HashSet<Integer>();
        for (var item : nota.itens()) {
            if (!numeros.add(item.numero()) || item.codigo().isBlank() || item.codigo().length()>60
                    || item.descricao().isBlank() || item.descricao().length()>300 || item.unidade().length()>20)
                erro("Identificação de produto inválida na linha " + item.numero() + ".");
            if (item.quantidade().signum()<=0 || item.quantidade().scale()>6 || item.quantidade().precision()>18
                    || item.valorBruto().signum()<0 || item.desconto().signum()<0
                    || item.desconto().compareTo(item.valorBruto())>0)
                erro("Quantidade ou valor inválido na linha " + item.numero() + ".");
            dinheiro(item.valorBruto()); dinheiro(item.desconto());
        }
    }

    public BigDecimal quantidade(ConfirmarNotaDTO.Item item) {
        if (item.quantidadeNota()==null || item.quantidadeNota().signum()<=0 || item.quantidadeNota().scale()>6
                || item.fator()==null || item.fator().signum()<=0 || item.fator().scale()>6)
            erro("Informe quantidade e fator de conversão positivos.");
        try {
            var quantidade = item.quantidadeNota().multiply(item.fator()).setScale(3, RoundingMode.UNNECESSARY);
            if (quantidade.precision()>15) erro("Quantidade acima do limite do estoque.");
            return quantidade;
        } catch (ArithmeticException e) { throw new FieldValidationException("quantidade", "A conversão exige mais de três casas decimais. Corrija a quantidade."); }
    }

    public void validarItem(ConfirmarNotaDTO.Item item, ItemNotaFiscalModel original) {
        if (item.unidadeEstoque()==null) erro("Escolha a unidade do estoque.");
        if (!item.embalagemConfirmada()) erro("Confira a unidade e o conteúdo da embalagem.");
        if (original.isIncerto() && !item.leituraConferida()) erro("Confira os dados extraídos da imagem.");
        if (item.produtoId()==null && (item.nomeProduto()==null || item.nomeProduto().isBlank() || item.categoriaId()==null))
            erro("Produtos novos precisam de nome e categoria.");
        dinheiro(item.valorEntrada());
        quantidade(item);
    }

    public void dinheiro(BigDecimal valor) {
        if (valor==null || valor.signum()<0 || valor.precision()-valor.scale()>16)
            erro("Informe um custo não negativo dentro do limite permitido.");
        try { valor.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { erro("O custo deve ter até duas casas decimais."); }
    }
    private void erro(String mensagem) { throw new FieldValidationException("notaFiscal", mensagem); }
}
