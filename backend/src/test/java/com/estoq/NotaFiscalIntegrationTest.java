package com.estoq;

import com.estoq.business.notasFiscais.*;
import com.estoq.business.categorias.*;
import com.estoq.business.produtos.*;
import com.estoq.business.usuarios.*;
import com.estoq.business.restaurantes.*;
import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.core.exceptions.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:estoq_notas_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class NotaFiscalIntegrationTest {
    @Autowired NotaFiscalService notas;
    @Autowired INotaFiscalRepository notaRepository;
    @Autowired ProdutoService produtos;
    @Autowired IProdutoRepository produtoRepository;
    @Autowired ICategoriaRepository categorias;
    @Autowired IUsuarioRepository usuarios;
    @Autowired IRestauranteRepository restaurantes;
    @Autowired IVinculoFornecedorRepository vinculos;
    @Autowired jakarta.persistence.EntityManager entityManager;
    Long categoriaId;

    @BeforeEach void preparar() {
        var restaurante=new RestauranteModel(); restaurante.setNome("Cozinha de teste"); restaurantes.saveAndFlush(restaurante);
        var usuario=new UsuarioModel(); usuario.setNome("Responsável"); usuario.setEmail("importador@test.local");
        usuario.setSenha("hash"); usuario.setPerfil(Perfil.ADMIN); usuario.setRestauranteId(restaurante.getId()); usuarios.saveAndFlush(usuario);
        var principal=UsuarioPrincipal.of(usuario);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        var categoria=new CategoriaModel(); categoria.setNome("Alimentos"); categorias.saveAndFlush(categoria); categoriaId=categoria.getId();
    }
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void bebidaAbreviadaSugereBebidasPreservandoDescricaoECustos() {
        var bebidas=new CategoriaModel(); bebidas.setNome("Bebidas"); categorias.saveAndFlush(bebidas);
        var hortifruti=new CategoriaModel(); hortifruti.setNome("Hortifruti"); categorias.saveAndFlush(hortifruti);
        var nota=analisar(NotaFiscalExemplo.CHAVE,"beb gaise h2o limao","UN");
        var item=nota.itens().getFirst();
        assertEquals(bebidas.getId(),item.categoriaId()); assertEquals("beb gaise h2o limao",item.descricao());
        assertEquals("DESCRICAO",item.origemCategoria()); assertFalse(item.revisarCategoria());
        assertEquals(0,new BigDecimal("20").compareTo(item.valorEntrada()));
        assertTrue(produtoRepository.findAllByAtivoTrue().isEmpty());
    }

    @Test void reanalisarRascunhoAtualizaSugestoesERejeitaVersaoAnterior() {
        var antiga=analisar(NotaFiscalExemplo.CHAVE,"beb gaise h2o limao","KG");
        assertNull(antiga.itens().getFirst().categoriaId());
        var bebidas=new CategoriaModel(); bebidas.setNome("Bebidas"); categorias.saveAndFlush(bebidas);
        // Simula a próxima requisição, sem reutilizar a entidade recém-inserida nesta sessão.
        entityManager.clear();
        var atual=analisar(NotaFiscalExemplo.CHAVE,"beb gaise h2o limao","KG");
        assertEquals(antiga.id(),atual.id()); assertTrue(atual.version()>antiga.version());
        assertEquals(bebidas.getId(),atual.itens().getFirst().categoriaId());
        assertThrows(ConflictException.class,()->notas.confirmar(antiga.id(),confirmar(antiga,null,categoriaId,BigDecimal.ONE)));
        var concluida=notas.confirmar(atual.id(),confirmar(atual,null,bebidas.getId(),BigDecimal.ONE));
        var repetida=analisar(NotaFiscalExemplo.CHAVE,"beb gaise h2o limao","KG");
        assertEquals(concluida.version(),repetida.version());
        assertEquals(concluida.itens(),repetida.itens());
    }

    @Test void catalogoExistenteMantemCategoriaMasAvisaDivergencia() {
        var bebidas=new CategoriaModel(); bebidas.setNome("Bebidas"); categorias.saveAndFlush(bebidas);
        var produto=new ProdutoDTO(); produto.setNome("BEB GAISE H2O LIMAO");
        produto.setCategoriaId(categoriaId); produto.setUnidadeMedida(UnidadeMedida.UN);
        var criado=produtos.criar(produto);
        var item=analisar(NotaFiscalExemplo.CHAVE,"BEB GAISE H2O LIMAO","UN").itens().getFirst();
        assertEquals(criado.getId(),item.produtoId()); assertEquals(categoriaId,item.categoriaId());
        assertEquals("NOME_EXATO",item.origemCategoria()); assertTrue(item.revisarCategoria());
        assertTrue(item.motivoCategoria().contains("categoria foi preservada"));
    }

    @Test void compostoAmbiguoPodeConfirmarSemClassificacaoComConferenciaFinal() {
        var laticinios=new CategoriaModel(); laticinios.setNome("Laticínios"); categorias.saveAndFlush(laticinios);
        var nota=analisar(NotaFiscalExemplo.CHAVE,"LEITE DE COCO","KG");
        assertNull(nota.itens().getFirst().categoriaId()); assertTrue(nota.itens().getFirst().revisarCategoria());
        var concluida=notas.confirmar(nota.id(),confirmar(nota,null,null,BigDecimal.ONE));
        assertEquals("Sem classificação",categorias.findById(concluida.itens().getFirst().categoriaId()).orElseThrow().getNome());
        assertFalse(concluida.itens().getFirst().revisarCategoria());
        assertEquals("REVISAO",concluida.itens().getFirst().origemCategoria());
    }

    NotaFiscalDTO analisar(String chave, String nome, String unidade) {
        var arquivo=new MockMultipartFile("arquivos","nota.xml","application/xml",NotaFiscalExemplo.xml(chave,nome,"AR1",unidade,"2.000","20.00").getBytes());
        return notas.analisar(List.of(arquivo));
    }
    ConfirmarNotaDTO confirmar(NotaFiscalDTO nota, Long produtoId, Long categoria, BigDecimal fator) {
        var item=nota.itens().getFirst();
        return new ConfirmarNotaDTO(nota.version(),List.of(new ConfirmarNotaDTO.Item(item.id(),true,produtoId,item.nomeProduto(),categoria,
                UnidadeMedida.KG,item.quantidadeNota(),fator,item.valorEntrada(),null,true,true)),true);
    }

    @Test void criaProdutoEEntradaSemDuplicarNaRepeticao() {
        var nota=analisar(NotaFiscalExemplo.CHAVE,"ARROZ 5KG","PC1");
        assertTrue(produtoRepository.findAllByAtivoTrue().isEmpty());
        var concluida=notas.confirmar(nota.id(),confirmar(nota,null,categoriaId,new BigDecimal("5")));
        assertTrue(concluida.confirmada());
        var produtoId=concluida.itens().getFirst().produtoId();
        assertEquals(0,new BigDecimal("10").compareTo(produtos.buscar(produtoId).getSaldoAtual()));
        notas.confirmar(nota.id(),confirmar(nota,null,categoriaId,new BigDecimal("5")));
        assertEquals(0,new BigDecimal("10").compareTo(produtos.buscar(produtoId).getSaldoAtual()));
        assertTrue(analisar(NotaFiscalExemplo.CHAVE,"ARROZ 5KG","PC1").confirmada());
        assertEquals(1,vinculos.findAllByAtivoTrue().size());
    }
    @Test void reconheceNomeEAprendeCodigoDoFornecedor() {
        var produto=new ProdutoDTO(); produto.setNome("Arroz");produto.setCategoriaId(categoriaId);produto.setUnidadeMedida(UnidadeMedida.KG);
        var criado=produtos.criar(produto);
        var nota=analisar(NotaFiscalExemplo.CHAVE,"ARROZ","KG");
        assertEquals(criado.getId(),nota.itens().getFirst().produtoId());
        notas.confirmar(nota.id(),confirmar(nota,criado.getId(),categoriaId,BigDecimal.ONE));
        var segunda=analisar(NotaFiscalExemplo.CHAVE.replace("000000011","000000021"),"ARROZ","KG");
        assertEquals(criado.getId(),segunda.itens().getFirst().produtoId());
        assertTrue(segunda.itens().getFirst().embalagemConfirmada());
    }
    @Test void falhaNaoCriaProdutoNemMovimentacao() {
        var nota=analisar(NotaFiscalExemplo.CHAVE,"ARROZ","KG");
        assertThrows(BusinessException.class,()->notas.confirmar(nota.id(),confirmar(nota,null,999999L,BigDecimal.ONE)));
        assertTrue(produtoRepository.findAllByAtivoTrue().isEmpty());
        assertFalse(notas.buscar(nota.id()).confirmada());
    }
    @Test void exigeConferenciaFinalERevisaoDeTodosOsItens() {
        var nota=analisar(NotaFiscalExemplo.CHAVE,"ARROZ","KG");
        var dto=confirmar(nota,null,categoriaId,BigDecimal.ONE);
        assertThrows(FieldValidationException.class,()->notas.confirmar(nota.id(),new ConfirmarNotaDTO(dto.version(),dto.itens(),false)));
        assertThrows(FieldValidationException.class,()->notas.confirmar(nota.id(),new ConfirmarNotaDTO(dto.version(),List.of(),true)));
    }
    @Test void conferenciaFinalDispensaCaixinhasIndividuaisMesmoComOcr() {
        var nota=analisar(NotaFiscalExemplo.CHAVE,"ARROZ 5KG","PC1");
        var original=notaRepository.findById(nota.id()).orElseThrow();
        original.getItens().getFirst().setIncerto(true);
        notaRepository.saveAndFlush(original);
        nota=notas.buscar(nota.id());
        var item=nota.itens().getFirst();
        var escolha=new ConfirmarNotaDTO.Item(item.id(),true,null,item.nomeProduto(),categoriaId,
            UnidadeMedida.KG,item.quantidadeNota(),new BigDecimal("5"),item.valorEntrada(),null,false,false);
        var concluida=notas.confirmar(nota.id(),new ConfirmarNotaDTO(nota.version(),List.of(escolha),true));
        assertTrue(concluida.confirmada());
        assertEquals(0,new BigDecimal("10").compareTo(produtos.buscar(concluida.itens().getFirst().produtoId()).getSaldoAtual()));
    }

    @Test void sugereCategoriaPeloNomeSemCriarProdutoNaLeitura() {
        var graos=new CategoriaModel();graos.setNome("Grãos");categorias.saveAndFlush(graos);
        var nota=analisar(NotaFiscalExemplo.CHAVE,"ARROZ 5KG","PC1");
        assertEquals(graos.getId(),nota.itens().getFirst().categoriaId());
        assertTrue(produtoRepository.findAllByAtivoTrue().isEmpty());
        var concluida=notas.confirmar(nota.id(),confirmar(nota,null,null,new BigDecimal("5")));
        assertEquals(graos.getId(),concluida.itens().getFirst().categoriaId());
        assertEquals(1,produtoRepository.findAllByAtivoTrue().size());
    }
    @Test void produtoDesconhecidoEntraSemCategoriaManualEReutilizaSemClassificacao() {
        var nota=analisar(NotaFiscalExemplo.CHAVE,"ARTIGO DESCONHECIDO","KG");
        assertNull(nota.itens().getFirst().categoriaId());
        assertEquals(1,categorias.findAllByAtivoTrue().size());
        var concluida=notas.confirmar(nota.id(),confirmar(nota,null,null,BigDecimal.ONE));
        var categoria=concluida.itens().getFirst().categoriaId();
        assertEquals("Sem classificação",categorias.findById(categoria).orElseThrow().getNome());
        var arquivo=new MockMultipartFile("arquivos","nota.xml","application/xml",
            NotaFiscalExemplo.xml(NotaFiscalExemplo.CHAVE.replace("000000011","000000021"),"OUTRO ARTIGO","AR2","KG","2","20.00").getBytes());
        var segunda=notas.analisar(List.of(arquivo));
        var outra=notas.confirmar(segunda.id(),confirmar(segunda,null,null,BigDecimal.ONE));
        assertEquals(categoria,outra.itens().getFirst().categoriaId());
        assertEquals(2,categorias.findAllByAtivoTrue().size());
    }

}
