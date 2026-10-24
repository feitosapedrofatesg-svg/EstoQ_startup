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
        assertEquals(0,BigDecimal.ONE.compareTo(item.getFator()));
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
        assertEquals(0,BigDecimal.ONE.compareTo(item.getFator()));
    }
}
