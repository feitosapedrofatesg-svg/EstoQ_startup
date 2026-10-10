package com.estoq.business.notasFiscais;

import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.produtos.ProdutoModel;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssociadorCategoriaNotaTest {
    private final AssociadorCategoriaNota associador = new AssociadorCategoriaNota();
    private CategoriaModel categoria(long id, String nome) {
        var categoria=new CategoriaModel(); categoria.setId(id); categoria.setNome(nome); return categoria;
    }
    @Test void nomesDasNotasReconhecemCategoriasComAcentos() {
        var categorias=List.of(categoria(1,"Carnes"),categoria(2,"Grãos"),categoria(3,"Limpeza"),categoria(4,"Bebidas"));
        assertEquals(1L,associador.sugerir("PICANHA BOVINA RESFRIADA",List.of(),categorias));
        assertEquals(2L,associador.sugerir("ARROZ BRANCO 5KG",List.of(),categorias));
        assertEquals(3L,associador.sugerir("DETERGENTE LIQUIDO 500ML",List.of(),categorias));
        assertEquals(4L,associador.sugerir("AGUA MINERAL 1,5L",List.of(),categorias));
        assertNull(associador.sugerir("ARTIGO DESCONHECIDO",List.of(),categorias));
    }
    @Test void catalogoDaCozinhaTemPrioridadeESugestoesAmbiguasFicamSemClassificacao() {
        var alimentos=categoria(1,"Alimentos");var graos=categoria(2,"Grãos");
        var arroz=new ProdutoModel();arroz.setNome("Arroz integral");arroz.setCategoria(alimentos);
        assertEquals(1L,associador.sugerir("ARROZ BRANCO 5KG",List.of(arroz),List.of(alimentos,graos)));
        var outro=new ProdutoModel();outro.setNome("Arroz branco");outro.setCategoria(graos);
        assertNull(associador.sugerir("ARROZ BRANCO 5KG",List.of(arroz,outro),List.of(alimentos,graos)));
        assertNull(associador.sugerir("PICANHA BOVINA",List.of(),List.of(categoria(3,"Carnes"),categoria(4,"Proteínas"))));
    }
}
