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
    @Test void bebidaAbreviadaNaoViraFrutaPorCausaDoSabor() {
        var categorias=List.of(categoria(1,"Bebidas"),categoria(2,"Hortifruti"),categoria(3,"Limpeza"),categoria(4,"Óleos e molhos"));
        for (var nome:List.of("beb gaise h2o limao", "BEB. GASEIF. H2O LIMÃO", "REFRI LIMÃO", "REFRIG LARANJA", "SUCO DE LIMÃO")) {
            var sugestao=associador.analisar(nome,List.of(),categorias);
            assertEquals(1L,sugestao.categoriaId(),nome);
            assertEquals("DESCRICAO",sugestao.origem());
            assertFalse(sugestao.revisar());
        }
        assertEquals(2L,associador.sugerir("LIMÃO",List.of(),categorias));
        assertEquals(2L,associador.sugerir("TOMATE",List.of(),categorias));
        assertEquals(3L,associador.sugerir("DETERGENTE LIMÃO",List.of(),categorias));
        assertEquals(4L,associador.sugerir("MOLHO DE TOMATE",List.of(),categorias));
    }

    @Test void compostosEConflitosPedemRevisaoSemEscolherPrimeiroResultado() {
        var categorias=List.of(categoria(1,"Laticínios"),categoria(2,"Carnes"),categoria(3,"Bebidas"),categoria(4,"Limpeza"));
        for (var nome:List.of("LEITE DE COCO", "CALDO DE CARNE", "KIT SUCO DETERGENTE", "ARTIGO SEM TIPO")) {
            var sugestao=associador.analisar(nome,List.of(),categorias);
            assertNull(sugestao.categoriaId(),nome);
            assertTrue(sugestao.revisar());
            assertNotNull(sugestao.motivo());
        }
        var produto=new ProdutoModel(); produto.setNome("Bebida láctea"); produto.setCategoria(categorias.getFirst());
        assertEquals(3L,associador.sugerir("BEB GAISE H2O LIMAO",List.of(produto),categorias));
        produto.setNome("Produto suco");
        assertNull(associador.sugerir("PRODUTO DESCONHECIDO",List.of(produto),categorias));
        assertEquals(9L,associador.sugerir("SUCO LIMAO",List.of(),List.of(categoria(9,"Bebidas da casa"))));
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
        assertEquals(2L,associador.sugerir("ARROZ BRANCO 5KG",List.of(arroz,outro),List.of(alimentos,graos)));
        assertNull(associador.sugerir("ARROZ PARBOILIZADO 5KG",List.of(arroz,outro),List.of(alimentos,graos)));
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

    @Test void nomeCompletoSemEmbalagemTemPrioridadeSobrePrimeiraPalavra() {
        var especiais=categoria(1,"Cafés especiais"); var mercearia=categoria(2,"Mercearia");
        var torrado=produto("Café torrado 500g",especiais);
        var soluvel=produto("Café solúvel",mercearia);
        for (var nome:List.of("CAFÉ TORRADO 1 KG", "CAFE TORRADO 1,5KG", "CAFE TORRADO 250 G")) {
            var sugestao=associador.analisar(nome,List.of(torrado,soluvel),List.of(especiais,mercearia));
            assertEquals(1L,sugestao.categoriaId(),nome);
            assertEquals("CATALOGO",sugestao.origem());
            assertTrue(sugestao.motivo().contains("nome normalizado"));
        }
        assertNull(associador.sugerir("CAFE TORRADO 1KG",
            List.of(torrado,produto("Café torrado 250g",mercearia)),List.of(especiais,mercearia)));
    }

    @Test void tiposDiferentesNaoHerdamCategoriaApenasPelaPrimeiraPalavra() {
        var carnes=categoria(1,"Carnes"); var peixes=categoria(2,"Peixes e frutos do mar");
        var hortifruti=categoria(3,"Hortifruti"); var mercearia=categoria(4,"Mercearia");
        var categorias=List.of(carnes,peixes,hortifruti,mercearia);
        assertEquals(2L,associador.sugerir("FILE DE TILAPIA 1KG",
            List.of(produto("Filé de frango",carnes)),categorias));
        assertEquals(1L,associador.sugerir("FILE DE FRANGO 1KG",
            List.of(produto("Filé de tilápia",peixes)),categorias));
        assertEquals(4L,associador.sugerir("BATATA PALHA 500G",
            List.of(produto("Batata inglesa",hortifruti)),categorias));
        assertEquals(3L,associador.sugerir("BATATA INGLESA",
            List.of(produto("Batata palha",mercearia)),categorias));
    }

    @Test void abreviacoesFiscaisECompostosEspecificosReconhecemTipoSemAlterarDescricao() {
        var categorias=List.of(categoria(1,"Carnes"),categoria(2,"Laticínios"),categoria(3,"Mercearia"),
            categoria(4,"Bebidas"),categoria(5,"Limpeza"),categoria(6,"Massas"));
        var casos=java.util.Map.ofEntries(
            java.util.Map.entry("CONTRAFILE BOV RESF 1KG",1L),
            java.util.Map.entry("QJO MUSSARELA 500G",2L),
            java.util.Map.entry("CR LEITE 200G",2L),
            java.util.Map.entry("BEB LACTEA MORANGO 1L",2L),
            java.util.Map.entry("FAR TRIGO 5KG",3L),
            java.util.Map.entry("ACUC CRISTAL 5KG",3L),
            java.util.Map.entry("DETERG LIMAO 500ML",5L),
            java.util.Map.entry("CREME DENTAL 90G",5L),
            java.util.Map.entry("MACARR ESPAGUETE 500G",6L));
        for (var caso:casos.entrySet()) assertEquals(caso.getValue(),
            associador.sugerir(caso.getKey(),List.of(),categorias),caso.getKey());
    }

    @Test void categoriasIndisponiveisEProdutosInativosNaoPropagamSugestoes() {
        var fora=categoria(1,"Categoria indisponível"); var graos=categoria(2,"Grãos");
        var sem=categoria(3,"Sem classificação");
        var inativo=produto("Arroz branco",fora); inativo.setAtivo(false);
        for (var produtos:List.of(List.of(produto("Arroz branco",fora)),List.of(inativo),
                List.of(produto("Arroz branco",sem)))) {
            assertEquals(2L,associador.sugerir("ARROZ BRANCO 5KG",produtos,List.of(graos,sem)));
        }
        var desativada=categoria(4,"Grãos"); desativada.setAtivo(false);
        assertNull(associador.sugerir("ARROZ BRANCO",List.of(),List.of(desativada)));
    }

    @Test void descricoesVaziasEDesconhecidasContinuamPedindoRevisao() {
        var categorias=List.of(categoria(1,"Mercearia"));
        for (var nome:new String[]{null,"","   ","ARTIGO 500G","FAROL AUTOMOTIVO"}) {
            var sugestao=associador.analisar(nome,List.of(),categorias);
            assertNull(sugestao.categoriaId(),String.valueOf(nome));
            assertTrue(sugestao.revisar());
        }
    }

    private ProdutoModel produto(String nome, CategoriaModel categoria) {
        var produto=new ProdutoModel(); produto.setNome(nome); produto.setCategoria(categoria); return produto;
    }

}
