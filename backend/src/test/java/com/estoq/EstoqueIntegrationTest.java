package com.estoq;

import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.categorias.ICategoriaRepository;
import com.estoq.business.consumos.ConsumoModel;
import com.estoq.business.consumos.ConsumoRequestDTO;
import com.estoq.business.consumos.ConsumoService;
import com.estoq.business.desperdicios.DesperdicioModel;
import com.estoq.business.desperdicios.DesperdicioRequestDTO;
import com.estoq.business.desperdicios.DesperdicioService;
import com.estoq.business.desperdicios.MotivoDesperdicio;
import com.estoq.business.entradas.EntradaRequestDTO;
import com.estoq.business.entradas.EntradaService;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.movimentacoesEstoque.IMovimentacaoEstoqueRepository;
import com.estoq.business.movimentacoesEstoque.MovimentacaoDTO;
import com.estoq.business.produtos.EstoqueService;
import com.estoq.business.produtos.ProdutoDTO;
import com.estoq.business.produtos.ProdutoService;
import com.estoq.business.produtos.UnidadeMedida;
import com.estoq.business.produtosAbertos.AbrirProdutoRequestDTO;
import com.estoq.business.produtosAbertos.ConsumirProdutoAbertoRequestDTO;
import com.estoq.business.produtosAbertos.DesperdicarProdutoAbertoRequestDTO;
import com.estoq.business.produtosAbertos.ProdutoAbertoService;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EstoqueIntegrationTest {

    @Autowired
    IUsuarioRepository users;
    @Autowired
    ICategoriaRepository categorias;
    @Autowired
    ProdutoService produtos;
    @Autowired
    EntradaService entradas;
    @Autowired
    ConsumoService consumos;
    @Autowired
    DesperdicioService desperdicios;
    @Autowired
    ProdutoAbertoService abertos;
    @Autowired
    EstoqueService estoque;
    @Autowired
    ILoteRepository lotes;
    @Autowired
    IMovimentacaoEstoqueRepository movimentos;
    Long produtoId;

    static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    static void igual(String esperado, BigDecimal valor) {
        assertEquals(0, n(esperado).compareTo(valor));
    }

    @BeforeEach
    void preparar() {
        var u = new UsuarioModel();
        u.setNome("Operador");
        u.setEmail("estoque@test.local");
        u.setSenha("hash");
        u.setPerfil(Perfil.ADMIN);
        users.saveAndFlush(u);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u.getEmail(), null, List.of()));
        var c = new CategoriaModel();
        c.setNome("Teste");
        categorias.saveAndFlush(c);
        var d = new ProdutoDTO();
        d.setNome("Arroz");
        d.setCategoriaId(c.getId());
        d.setUnidadeMedida(UnidadeMedida.KG);
        produtoId = produtos.criar(d).getId();
    }

    @AfterEach
    void limparSessao() {
        SecurityContextHolder.clearContext();
    }

    Long entrada(String qtd, String valor, LocalDate validade) {
        return entradas.registrarEntrada(new EntradaRequestDTO(produtoId, n(qtd), n(valor), UnidadeMedida.KG, validade, null, false)).movimentacoes().getFirst().loteId();
    }

    @Test
    void aberturaConsumoEDescarteMantemRestanteDentroDoLote() {
        Long loteId = entrada("5", "200", LocalDate.now().plusDays(3));
        var a = abertos.abrirEmbalagem(new AbrirProdutoRequestDTO(produtoId, loteId, n("2"), n("0.5"), null, null));
        igual("4.5", lotes.findById(loteId).orElseThrow().getQuantidadeAtual());
        igual("4.5", estoque.obterSaldo(produtoId));
        igual("1.5", a.quantidadeRestante());
        a = abertos.consumirProdutoAberto(a.id(), new ConsumirProdutoAbertoRequestDTO(n("0.7"), null, null, null));
        igual("3.8", a.saldoAtual());
        igual("0.8", a.quantidadeRestante());
        a = abertos.desperdicarProdutoAberto(a.id(), new DesperdicarProdutoAbertoRequestDTO(n("0.8"), null, null, null));
        igual("3", a.quantidadeLoteAtual());
        assertTrue(a.finalizado());
        var perdas = movimentos.findAll().stream().filter(DesperdicioModel.class::isInstance).map(DesperdicioModel.class::cast).toList();
        igual("32", perdas.getFirst().getValorPrejuizo());
        assertEquals(MotivoDesperdicio.SOBRA_NAO_APROVEITADA, perdas.getFirst().getMotivo());
        igual("48", movimentos.findAll().stream().filter(ConsumoModel.class::isInstance).map(m -> ((ConsumoModel) m).getCustoConsumo()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void aberturaSemUsoNaoCriaConsumoNemBaixa() {
        Long loteId = entrada("5", "50", LocalDate.now().plusDays(3));
        long antes = movimentos.count();
        var a = abertos.abrirEmbalagem(new AbrirProdutoRequestDTO(produtoId, loteId, n("3"), BigDecimal.ZERO, null, null));
        igual("5", a.saldoAtual());
        igual("3", a.quantidadeRestante());
        assertEquals(antes, movimentos.count());
        assertThrows(ConflictException.class, () -> abertos.abrirEmbalagem(new AbrirProdutoRequestDTO(produtoId, loteId, n("3"), BigDecimal.ZERO, null, null)));
    }

    @Test
    void fifoPorValidadeConsomeAbertoSemDesalinharRastreio() {
        Long tardio = entrada("5", "50", LocalDate.now().plusDays(10));
        Long cedo = entrada("3", "60", LocalDate.now().plusDays(1));
        var a = abertos.abrirEmbalagem(new AbrirProdutoRequestDTO(produtoId, cedo, n("2"), BigDecimal.ZERO, null, null));
        var result = consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, n("4"), null, null));
        igual("0", lotes.findById(cedo).orElseThrow().getQuantidadeAtual());
        igual("4", lotes.findById(tardio).orElseThrow().getQuantidadeAtual());
        assertTrue(abertos.buscar(a.id()).finalizado());
        igual("70", result.movimentacoes().stream().map(MovimentacaoDTO::custoConsumo).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void rejeitaConsumoVencidoIncluindoItemAberto() {
        Long loteId = entrada("5", "50", LocalDate.now().plusDays(1));
        var a = abertos.abrirEmbalagem(new AbrirProdutoRequestDTO(produtoId, loteId, n("2"), BigDecimal.ZERO, null, null));
        lotes.findById(loteId).orElseThrow().setDataValidade(LocalDate.now().minusDays(1));
        lotes.flush();
        assertThrows(ConflictException.class, () -> abertos.consumirProdutoAberto(a.id(), new ConsumirProdutoAbertoRequestDTO(n("1"), null, null, null)));
    }

    @Test
    void rejeitaDesperdicioAcimaDoRestante() {
        Long loteId = entrada("5", "50", LocalDate.now().plusDays(1));
        var a = abertos.abrirEmbalagem(new AbrirProdutoRequestDTO(produtoId, loteId, n("2"), n("1"), null, null));
        assertThrows(ConflictException.class, () -> abertos.desperdicarProdutoAberto(a.id(), new DesperdicarProdutoAbertoRequestDTO(n("2"), null, null, null)));
        igual("4", estoque.obterSaldo(produtoId));
    }

    @Test
    void consultaComVersaoAntigaRecebeConflito() {
        Long loteId = entrada("5", "50", LocalDate.now().plusDays(1));
        Long version = lotes.findById(loteId).orElseThrow().getVersion();
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, loteId, n("1"), version, null));
        assertThrows(ConflictException.class, () -> consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, loteId, n("1"), version, null)));
    }

    @Test
    void entradaSemValidadeEConsumivelSemVencer() {
        Long loteId = entrada("5", "50", null);
        var lote = lotes.findById(loteId).orElseThrow();
        assertNull(lote.getDataValidade());
        assertFalse(lote.estaVencido(LocalDate.now()));
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, n("2"), null, null));
        igual("3", estoque.obterSaldo(produtoId));
    }

    @Test
    void entradaCustoZeroExigeFlagSemCusto() {
        assertThrows(FieldValidationException.class,
                () -> entradas.registrarEntrada(new EntradaRequestDTO(produtoId, n("5"), BigDecimal.ZERO, UnidadeMedida.KG, LocalDate.now().plusDays(3), null, false)));
        var mov = entradas.registrarEntrada(new EntradaRequestDTO(produtoId, n("5"), BigDecimal.ZERO, UnidadeMedida.KG, LocalDate.now().plusDays(3), null, true)).movimentacoes().getFirst();
        igual("5", estoque.obterSaldo(produtoId));
        igual("0", lotes.findById(mov.loteId()).orElseThrow().getPrecoUnitario());
        assertThrows(FieldValidationException.class,
                () -> entradas.registrarEntrada(new EntradaRequestDTO(produtoId, n("5"), n("50"), UnidadeMedida.KG, LocalDate.now().plusDays(3), null, true)));
    }
}