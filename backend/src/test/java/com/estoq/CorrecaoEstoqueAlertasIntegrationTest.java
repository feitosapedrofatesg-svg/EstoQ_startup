package com.estoq;

import com.estoq.business.alertas.*;
import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.business.categorias.*;
import com.estoq.business.entradas.*;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.produtos.*;
import com.estoq.business.relatorios.RelatorioService;
import com.estoq.business.usuarios.*;
import com.estoq.core.helpers.HorarioOperacao;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CorrecaoEstoqueAlertasIntegrationTest {
    @MockitoBean HorarioOperacao horario;
    @Autowired IUsuarioRepository usuarios;
    @Autowired ICategoriaRepository categorias;
    @Autowired ProdutoService produtos;
    @Autowired IProdutoRepository catalogo;
    @Autowired EntradaService entradas;
    @Autowired ILoteRepository lotes;
    @Autowired AlertaService alertas;
    @Autowired EstoqueService estoque;
    @Autowired RelatorioService relatorios;
    Long produtoId;
    final LocalDate dia = LocalDate.of(2026, 10, 17);

    @BeforeEach void preparar() {
        when(horario.hoje()).thenReturn(dia);
        var usuario = new UsuarioModel(); usuario.setNome("Responsável"); usuario.setEmail("alertas@teste.local");
        usuario.setSenha("hash-ficticio"); usuario.setPerfil(Perfil.ADMIN); usuarios.saveAndFlush(usuario);
        var principal = UsuarioPrincipal.of(usuario);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        var categoria = new CategoriaModel(); categoria.setNome("Alimentos"); categorias.saveAndFlush(categoria);
        var produto = new ProdutoDTO(); produto.setNome("Arroz"); produto.setUnidadeMedida(UnidadeMedida.KG);
        produto.setCategoriaId(categoria.getId()); produtoId = produtos.criar(produto).getId();
        catalogo.findByIdAndAtivoTrue(produtoId).orElseThrow().getParametro().setDiasAlertaVencimento(3);
    }

    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void novosProdutosSinalizamAbaixoDeTresNaUnidadeDeCadastro() {
        long categoriaId = catalogo.findByIdAndAtivoTrue(produtoId).orElseThrow().getCategoria().getId();
        for (var unidade : UnidadeMedida.values()) {
            var dto = new ProdutoDTO(); dto.setNome("Produto " + unidade); dto.setUnidadeMedida(unidade);
            dto.setCategoriaId(categoriaId);
            long id = produtos.criar(dto).getId();
            entradas.registrarEntrada(new EntradaRequestDTO(id, new BigDecimal("2"), new BigDecimal("10"),
                    unidade, dia.plusDays(30), null, false));
            assertTrue(estoque.listar(false, true).stream().anyMatch(p -> p.produtoId().equals(id)));
            assertTrue(alertas.listar(false, Perfil.ADMIN).stream()
                    .anyMatch(a -> a.tipo() == TipoAlerta.ESTOQUE_BAIXO && a.produtoId().equals(id)));
            entradas.registrarEntrada(new EntradaRequestDTO(id, BigDecimal.ONE, new BigDecimal("5"),
                    unidade, dia.plusDays(30), null, false));
            assertTrue(estoque.listar(false, true).stream().noneMatch(p -> p.produtoId().equals(id)));
            assertTrue(alertas.listar(false, Perfil.ADMIN).stream()
                    .noneMatch(a -> a.tipo() == TipoAlerta.ESTOQUE_BAIXO && a.produtoId().equals(id)));
        }
    }

    @Test void editarProdutoPreservaMinimoPersonalizadoInclusiveZero() {
        var produto = catalogo.findByIdAndAtivoTrue(produtoId).orElseThrow();
        var parametro = produto.getParametro();
        for (var minimo : java.util.List.of(new BigDecimal("7"), BigDecimal.ZERO)) {
            parametro.setEstoqueMinimo(minimo); parametro.setEstoqueMedio(new BigDecimal("8"));
            parametro.setEstoqueMaximo(new BigDecimal("9")); catalogo.flush();
            var dto = produtos.buscar(produtoId); dto.setNome("Arroz atualizado " + minimo);
            produtos.atualizar(produtoId, dto);
            assertEquals(0, minimo.compareTo(produtos.encontrar(produtoId).getParametro().getEstoqueMinimo()));
        }
    }

    @Test void vencimentoMudaSemNovaMovimentacaoENaoReabreAvisoVisto() {
        var movimento = entradas.registrarEntrada(new EntradaRequestDTO(produtoId, new BigDecimal("10"), new BigDecimal("100"),
                UnidadeMedida.KG, dia.plusDays(1), null, false)).movimentacoes().getFirst();
        var primeiro = alertas.listar(false, Perfil.ADMIN).stream().filter(a -> a.tipo() == TipoAlerta.PROXIMO_VENCIMENTO).findFirst().orElseThrow();
        alertas.marcarVisualizado(primeiro.id());
        assertTrue(alertas.listar(false, Perfil.ADMIN).stream().noneMatch(a -> a.tipo() == TipoAlerta.PROXIMO_VENCIMENTO));

        when(horario.hoje()).thenReturn(dia.plusDays(2));
        var vencidos = alertas.listar(false, Perfil.ADMIN);
        assertEquals(1, vencidos.stream().filter(a -> a.tipo() == TipoAlerta.VENCIDO).count());
        assertTrue(alertas.listar(null, Perfil.ADMIN).stream().noneMatch(a -> a.tipo() == TipoAlerta.PROXIMO_VENCIMENTO));
        lotes.findById(movimento.loteId()).orElseThrow().setQuantidadeAtual(BigDecimal.ZERO);
        assertTrue(alertas.listar(false, Perfil.ADMIN).stream().noneMatch(a -> a.tipo() == TipoAlerta.VENCIDO));
    }

    @Test void estoqueVencidoNaoImpedeAlertaOuSugestaoDeCompra() {
        entradas.registrarEntrada(new EntradaRequestDTO(produtoId, new BigDecimal("10"), new BigDecimal("100"),
                UnidadeMedida.KG, dia.minusDays(1), null, false));
        var parametro = catalogo.findByIdAndAtivoTrue(produtoId).orElseThrow().getParametro();
        parametro.setEstoqueMinimo(BigDecimal.ONE);
        parametro.setConsumoMedioDiario(new BigDecimal("2")); parametro.setTempoReposicaoDias(3);
        var posicao = estoque.listar(false, false).getFirst();
        assertEquals(0, new BigDecimal("10").compareTo(posicao.saldoAtual()));
        assertEquals(0, BigDecimal.ZERO.compareTo(posicao.saldoDisponivelConsumo()));
        assertTrue(posicao.abaixoDoMinimo());
        assertEquals(0, new BigDecimal("6").compareTo(relatorios.reposicaoSugerida(7).getFirst().quantidadeSugerida()));
        assertTrue(alertas.listar(false, Perfil.ADMIN).stream().anyMatch(a -> a.tipo() == TipoAlerta.ESTOQUE_BAIXO));
    }
}
