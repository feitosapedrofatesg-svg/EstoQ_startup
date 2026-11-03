package com.estoq.business.notasFiscais;

import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.produtos.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssociadorProdutoNotaTest {
    private final AssociadorProdutoNota associador = new AssociadorProdutoNota();

    private ProdutoModel produto(long id, String nome) {
        var categoria = new CategoriaModel(); categoria.setId(10L);
        var produto = new ProdutoModel(); produto.setId(id); produto.setNome(nome);
        produto.setCategoria(categoria); produto.setUnidadeMedida(UnidadeMedida.L);
        return produto;
    }

    private ItemNotaFiscalModel item(String descricao, String unidade) {
        var item = new ItemNotaFiscalModel(); item.setDescricao(descricao); item.setUnidadeNota(unidade);
        return item;
    }

    private VinculoFornecedorModel vinculo() {
        var vinculo = new VinculoFornecedorModel(); vinculo.setProdutoId(1L);
        vinculo.setDescricao("SUCO LIMÃO"); vinculo.setUnidadeNota("CX");
        vinculo.setUnidadeEstoque(UnidadeMedida.L); vinculo.setFator(new BigDecimal("12"));
        return vinculo;
    }

    @Test void historicoCompativelMantemProdutoCategoriaEFator() {
        var item = item("suco limao", "cx");
        associador.sugerir(item,List.of(produto(1,"Suco da casa")),vinculo());
        assertEquals(1L,item.getProdutoId()); assertEquals(10L,item.getCategoriaId());
        assertEquals("FORNECEDOR",item.getOrigemCategoria());
        assertEquals(0,new BigDecimal("12").compareTo(item.getFator()));
        assertTrue(item.isEmbalagemConfirmada()); assertFalse(item.isRevisarCategoria());
    }

    @Test void descricaoAlteradaNaoReutilizaProdutoNemConversaoAntiga() {
        var item = item("BEB GAISE H2O LIMAO", "CX");
        associador.sugerir(item,List.of(produto(1,"Suco da casa")),vinculo());
        assertNull(item.getProdutoId()); assertNull(item.getCategoriaId());
        assertNull(item.getFator());
        assertFalse(item.isEmbalagemConfirmada()); assertTrue(item.isRevisarCategoria());
        assertTrue(item.getMotivoCategoria().contains("fator anterior não foi reutilizado"));
    }

    @Test void unidadeAlteradaReidentificaPorNomeSemReaproveitarFator() {
        var item = item("SUCO LIMÃO", "L");
        associador.sugerir(item,List.of(produto(1,"SUCO LIMAO")),vinculo());
        assertEquals(1L,item.getProdutoId()); assertEquals("NOME_EXATO",item.getOrigemCategoria());
        assertEquals(0,BigDecimal.ONE.compareTo(item.getFator()));
        assertTrue(item.isRevisarCategoria());
    }

    @Test void codigoDeBarrasExatoPrevaleceSobreNomeECategoriaDeSabor() {
        var bebida = produto(2,"Bebida H2O"); bebida.setCodigoBarras("7894900011517");
        var item = item("BEB GAISE H2O LIMAO", "L"); item.setCodigoBarras(bebida.getCodigoBarras());
        associador.sugerir(item,List.of(produto(1,"SUCO LIMAO"),bebida),vinculo());
        assertEquals(2L,item.getProdutoId()); assertEquals(10L,item.getCategoriaId());
        assertEquals("CODIGO_BARRAS",item.getOrigemCategoria());
    }

    @Test void reanaliseRemoveSugestaoAntigaSeProdutoSaiuDoCatalogo() {
        var item = item("SUCO LIMÃO", "CX");
        associador.sugerir(item,List.of(produto(1,"Suco da casa")),vinculo());
        associador.sugerir(item,List.of(),null);
        assertNull(item.getProdutoId()); assertNull(item.getCategoriaId());
        assertNull(item.getOrigemCategoria()); assertFalse(item.isEmbalagemConfirmada());
        assertEquals(UnidadeMedida.UN,item.getUnidadeEstoque());
        assertNull(item.getFator());
    }
    @Test void pacotesEmUnConvertemPesoSemMultiplicarNotasQueJaVemEmKg() {
        var arroz = produto(3,"ARROZ 5KG"); arroz.setUnidadeMedida(UnidadeMedida.KG);
        for (var un : List.of("UN","UN1","PC1","PC")) {
            var linha = item("ARROZ 5KG", un); associador.sugerir(linha,List.of(arroz),null);
            assertEquals(UnidadeMedida.KG,linha.getUnidadeEstoque());
            assertEquals(0,new BigDecimal("5").compareTo(linha.getFator()));
        }
        var peso = item("ARROZ 5KG","KG"); associador.sugerir(peso,List.of(arroz),null);
        assertEquals(0,BigDecimal.ONE.compareTo(peso.getFator()));
        var gramas = item("ARROZ 500G","UN"); arroz.setNome("ARROZ 500G");
        associador.sugerir(gramas,List.of(arroz),null);
        assertEquals(0,new BigDecimal("0.5").compareTo(gramas.getFator()));
    }
    @Test void caixasSemHistoricoMultipacksEHistoricoConflitanteExigemConteudo() {
        for (var descricao : List.of("ARROZ 5KG","ARROZ 12X5KG","ARROZ")) {
            var linha = item(descricao,"CX"); associador.sugerir(linha,List.of(),null);
            assertNull(linha.getFator());
        }
        var arroz = produto(1,"ARROZ 5KG"); arroz.setUnidadeMedida(UnidadeMedida.KG);
        var anterior = vinculo(); anterior.setDescricao("ARROZ 5KG"); anterior.setUnidadeNota("UN");
        anterior.setUnidadeEstoque(UnidadeMedida.KG); anterior.setFator(BigDecimal.ONE);
        var linha = item("ARROZ 5KG","UN"); associador.sugerir(linha,List.of(arroz),anterior);
        assertNull(linha.getFator()); assertFalse(linha.isEmbalagemConfirmada());
    }
}
