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
    @Autowired ProdutoService produtos;
    @Autowired IProdutoRepository produtoRepository;
    @Autowired ICategoriaRepository categorias;
    @Autowired IUsuarioRepository usuarios;
    @Autowired IRestauranteRepository restaurantes;
    @Autowired IVinculoFornecedorRepository vinculos;
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
    @Test void exigeRevisaoDosCustosDaEmbalagemEDeTodosOsItens() {
        var nota=analisar(NotaFiscalExemplo.CHAVE,"ARROZ","KG");
        var dto=confirmar(nota,null,categoriaId,BigDecimal.ONE);
        assertThrows(FieldValidationException.class,()->notas.confirmar(nota.id(),new ConfirmarNotaDTO(dto.version(),dto.itens(),false)));
        assertThrows(FieldValidationException.class,()->notas.confirmar(nota.id(),new ConfirmarNotaDTO(dto.version(),List.of(),true)));
    }
}
