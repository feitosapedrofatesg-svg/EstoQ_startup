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
        assertEquals(3L,associador.sugerir("PICANHA BOVINA",List.of(),List.of(categoria(3,"Carnes"),categoria(4,"Proteínas"))));
        assertNull(associador.sugerir("PICANHA BOVINA",List.of(),List.of(categoria(3,"Carnes bovinas"),categoria(4,"Proteínas"))));
    }
    @Test void primeiraNotaReconheceTodosOsGruposPadraoSemConfundirAliases() {
        var categorias=new java.util.ArrayList<CategoriaModel>();
        for(int i=0;i<com.estoq.business.categorias.CategoriaPadraoService.NOMES.size();i++)
            categorias.add(categoria(i+1,com.estoq.business.categorias.CategoriaPadraoService.NOMES.get(i)));
        var casos=java.util.Map.ofEntries(
            java.util.Map.entry("PICANHA BOVINA","Carnes"), java.util.Map.entry("TILAPIA","Peixes e frutos do mar"),
            java.util.Map.entry("LEITE INTEGRAL","Laticínios"), java.util.Map.entry("ARROZ BRANCO 5KG","Grãos"),
            java.util.Map.entry("TOMATE","Hortifruti"), java.util.Map.entry("MACARRAO PENNE","Massas"),
            java.util.Map.entry("SAL REFINADO","Temperos e condimentos"), java.util.Map.entry("OLEO DE SOJA","Óleos e molhos"),
            java.util.Map.entry("MOLHO DE TOMATE","Óleos e molhos"), java.util.Map.entry("ACUCAR CRISTAL","Mercearia"),
            java.util.Map.entry("SUCO DE LARANJA","Bebidas"), java.util.Map.entry("DETERGENTE","Limpeza"),
            java.util.Map.entry("GUARDANAPO","Descartáveis e embalagens"));
        for(var caso:casos.entrySet()) {
            var esperado=categorias.stream().filter(c -> c.getNome().equals(caso.getValue())).findFirst().orElseThrow();
            assertEquals(esperado.getId(),associador.sugerir(caso.getKey(),List.of(),categorias),caso.getKey());
        }
        assertNull(associador.sugerir("ARTIGO SEM NOME CONHECIDO",List.of(),categorias));
    }

}
