package com.estoq;

import com.estoq.business.balancos.BalancoService;
import com.estoq.business.balancos.ContagemRequestDTO;
import com.estoq.business.balancos.CriarBalancoRequestDTO;
import com.estoq.business.balancos.StatusBalanco;
import com.estoq.business.balancos.TipoBalanco;
import com.estoq.business.alertas.AlertaService;
import com.estoq.business.alertas.IAlertaRepository;
import com.estoq.business.alertas.TipoAlerta;
import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.categorias.ICategoriaRepository;
import com.estoq.business.configuracoesBalanco.ConfiguracaoBalancoDTO;
import com.estoq.business.configuracoesBalanco.ConfiguracaoBalancoService;
import com.estoq.business.configuracoesBalanco.IConfiguracaoBalancoRepository;
import com.estoq.business.configuracoesBalanco.PeriodicidadeBalanco;
import com.estoq.business.consumos.ConsumoRequestDTO;
import com.estoq.business.consumos.ConsumoService;
import com.estoq.business.desperdicios.DesperdicioRequestDTO;
import com.estoq.business.desperdicios.DesperdicioService;
import com.estoq.business.desperdicios.MotivoDesperdicio;
import com.estoq.business.entradas.EntradaRequestDTO;
import com.estoq.business.entradas.EntradaService;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.movimentacoesEstoque.IMovimentacaoEstoqueRepository;
import com.estoq.business.movimentacoesEstoque.TipoMovimentacao;
import com.estoq.business.parametrosCmv.ParametroCmvDTO;
import com.estoq.business.parametrosCmv.ParametroCmvService;
import com.estoq.business.produtos.ProdutoDTO;
import com.estoq.business.produtos.ProdutoService;
import com.estoq.business.produtos.UnidadeMedida;
import com.estoq.business.relatorios.CmvMensalDTO;
import com.estoq.business.relatorios.RelatorioService;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BalancoCmvIntegrationTest {

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
    BalancoService balancos;
    @Autowired
    ILoteRepository lotes;
    @Autowired
    IMovimentacaoEstoqueRepository movimentos;
    @Autowired
    RelatorioService relatorios;
    @Autowired
    ParametroCmvService parametrosCmv;
    @Autowired
    AlertaService alertas;
    @Autowired
    IAlertaRepository alertaRepository;
    @Autowired
    ConfiguracaoBalancoService configuracoes;
    @Autowired
    IConfiguracaoBalancoRepository configRepository;
    Long produtoId;

    @BeforeEach
    void preparar() {
        var u = new UsuarioModel();
        u.setNome("Gerente");
        u.setEmail("gerente@test.local");
        u.setSenha("hash");
        u.setPerfil(Perfil.ADMIN);
        users.saveAndFlush(u);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u.getEmail(), null, List.of()));
        var c = new CategoriaModel();
        c.setNome("Categoria");
        categorias.saveAndFlush(c);
        var d = new ProdutoDTO();
        d.setNome("Açúcar");
        d.setCategoriaId(c.getId());
        d.setUnidadeMedida(UnidadeMedida.KG);
        produtoId = produtos.criar(d).getId();
        entradas.registrarEntrada(new EntradaRequestDTO(produtoId, new BigDecimal("10"), new BigDecimal("200"), UnidadeMedida.KG, LocalDate.now().plusDays(60), null, false));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void balancoEuSerumDebitoDistribuiFifo() {
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id());
        assertEquals(StatusBalanco.EM_ANDAMENTO, iniciado.status());
        var item = iniciado.itens().getFirst();
        assertEquals(0, new BigDecimal("10").compareTo(item.quantidadeSistema()));
        var contado = balancos.registrarContagem(iniciado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("8")));
        var confirmado = balancos.confirmar(contado.id());
        assertEquals(StatusBalanco.CONCLUIDO, confirmado.status());
        var ajustado = balancos.gerarAjustes(confirmado.id());
        assertTrue(ajustado.itens().getFirst().ajusteAplicado());
        assertEquals(0, new BigDecimal("2").compareTo(ajustado.itens().getFirst().diferenca().abs()));
        assertEquals(0, new BigDecimal("8").compareTo(lotes.saldo(produtoId)));
        var ajustes = movimentos.findAll().stream().filter(m -> m.getTipo() == TipoMovimentacao.AJUSTE).toList();
        assertEquals(1, ajustes.size());
        assertEquals(0, new BigDecimal("2").compareTo(ajustes.getFirst().getDelta().abs()));
    }

    @Test
    void balancoSuperavitSomaNoLoteDisponivel() {
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id());
        var item = iniciado.itens().getFirst();
        balancos.registrarContagem(iniciado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("12")));
        var confirmado = balancos.confirmar(iniciado.id());
        balancos.gerarAjustes(confirmado.id());
        assertEquals(0, new BigDecimal("12").compareTo(lotes.saldo(produtoId)));
        var ajustes = movimentos.findAll().stream().filter(m -> m.getTipo() == TipoMovimentacao.AJUSTE).toList();
        assertEquals(1, ajustes.size());
        assertEquals(0, new BigDecimal("2").compareTo(ajustes.getFirst().getDelta().abs()));
    }

    @Test
    void naoConfirmaSemContagem() {
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id());
        assertThrows(ConflictException.class, () -> balancos.confirmar(iniciado.id()));
    }

    @Test
    void cmvUsaMovimentacoesELote() {
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, new BigDecimal("2"), null, null));
        desperdicios.registrarDesperdicio(new DesperdicioRequestDTO(produtoId, null, new BigDecimal("1"), MotivoDesperdicio.DETERIORACAO, null, null, null));
        var fim = LocalDateTime.now().plusDays(1);
        var cmv = relatorios.calcularCmv(fim.minusDays(30), fim, new BigDecimal("100"));
        assertEquals(0, new BigDecimal("0").compareTo(cmv.valorEstoqueInicial()));
        assertEquals(0, new BigDecimal("200").compareTo(cmv.valorCompras()));
        assertEquals(0, new BigDecimal("140").compareTo(cmv.valorEstoqueFinal()));
        assertEquals(0, new BigDecimal("60").compareTo(cmv.cmv()));
        assertEquals(0, new BigDecimal("40").compareTo(cmv.valorConsumoRegistrado()));
        assertEquals(0, new BigDecimal("20").compareTo(cmv.valorDesperdicio()));
        assertEquals(0, new BigDecimal("60.0000").compareTo(cmv.cmvPercentual()));
        assertEquals(0, new BigDecimal("0").compareTo(cmv.valorPerdasNaoExplicadas()));
        var medio = relatorios.consumoMedio(fim.minusDays(30), fim);
        assertEquals(0, new BigDecimal("2").compareTo(medio.getFirst().totalConsumidoPeriodo()));
        assertEquals(ChronoUnit.DAYS.between(fim.minusDays(30), fim), medio.getFirst().diasPeriodo());
    }

    @Test
    void parametroCmvRestringePercentual() {
        assertNull(parametrosCmv.obterAtual().percentualIdeal());
        var salvo = parametrosCmv.salvar(new ParametroCmvDTO(null, null, new BigDecimal("30")));
        assertEquals(0, new BigDecimal("30").compareTo(parametrosCmv.obterAtual().percentualIdeal()));
        assertThrows(FieldValidationException.class,
                () -> parametrosCmv.salvar(new ParametroCmvDTO(null, null, new BigDecimal("120"))));
        assertEquals(0, new BigDecimal("30").compareTo(parametrosCmv.obterAtual().percentualIdeal()));
    }

    @Test
    void balancoSuperavitCriaLoteNovoConsumivel() {
        var categoria = categorias.findAll().getFirst();
        var d = new ProdutoDTO();
        d.setNome("Produto sem estoque");
        d.setCategoriaId(categoria.getId());
        d.setUnidadeMedida(UnidadeMedida.KG);
        var semEstoque = produtos.criar(d).getId();
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id());
        var existente = iniciado.itens().stream().filter(i -> i.produtoId().equals(produtoId)).findFirst().orElseThrow();
        var novo = iniciado.itens().stream().filter(i -> i.produtoId().equals(semEstoque)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0").compareTo(novo.quantidadeSistema()));
        balancos.registrarContagem(iniciado.id(), existente.id(), new ContagemRequestDTO(existente.quantidadeSistema()));
        balancos.registrarContagem(iniciado.id(), novo.id(), new ContagemRequestDTO(new BigDecimal("3")));
        var confirmado = balancos.confirmar(iniciado.id());
        balancos.gerarAjustes(confirmado.id());
        assertEquals(0, new BigDecimal("3").compareTo(lotes.saldo(semEstoque)));
        var ajustes = movimentos.findAll().stream().filter(m -> m.getTipo() == TipoMovimentacao.AJUSTE).toList();
        assertEquals(1, ajustes.size());
        assertEquals(0, new BigDecimal("3").compareTo(ajustes.getFirst().getDelta().abs()));
        consumos.registrarConsumo(new ConsumoRequestDTO(semEstoque, null, new BigDecimal("1"), null, null));
        assertEquals(0, new BigDecimal("2").compareTo(lotes.saldo(semEstoque)));
    }

    @Test
    void cmvMensuraPerdasNaoRegistradasComoGap() {
        var lote = lotes.findAll().getFirst();
        lote.baixar(new BigDecimal("2"));
        lotes.flush();
        var fim = LocalDateTime.now().plusDays(1);
        var cmv = relatorios.calcularCmv(fim.minusDays(30), fim, null);
        assertEquals(0, new BigDecimal("40").compareTo(cmv.cmv()));
        assertEquals(0, new BigDecimal("40").compareTo(cmv.valorPerdasNaoExplicadas()));
    }

    @Test
    void cicloAlertasBalancoGeraLimpaEAvanca() {
        var cfg = configuracoes.salvar(new ConfiguracaoBalancoDTO(null, null, PeriodicidadeBalanco.DIARIA, null, null));
        var guardado = configRepository.findByIdAndAtivoTrue(cfg.id()).orElseThrow();
        guardado.setProximaExecucao(LocalDate.now());
        configRepository.flush();
        alertas.avaliarAgendamentos();
        assertEquals(1, contarAbertos(TipoAlerta.BALANCO_PENDENTE));
        assertTrue(configRepository.findByIdAndAtivoTrue(cfg.id()).orElseThrow().getProximaExecucao().isAfter(LocalDate.now()));
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id());
        var item = iniciado.itens().getFirst();
        balancos.registrarContagem(iniciado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("8")));
        var confirmado = balancos.confirmar(iniciado.id());
        assertEquals(0, contarAbertos(TipoAlerta.BALANCO_PENDENTE));
        assertEquals(1, contarAbertos(TipoAlerta.DIFERENCA_ESTOQUE));
        balancos.gerarAjustes(confirmado.id());
        assertEquals(0, contarAbertos(TipoAlerta.DIFERENCA_ESTOQUE));
    }

    @Test
    void relatoriosAgregadosDesperdicioConsumoECmvMensal() {
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, new BigDecimal("2"), null, null));
        desperdicios.registrarDesperdicio(new DesperdicioRequestDTO(produtoId, null, new BigDecimal("1"), MotivoDesperdicio.DETERIORACAO, null, null, null));
        var fim = LocalDateTime.now().plusDays(1);
        var inicio = fim.minusDays(30);
        var agregado = relatorios.desperdicioAgregado(inicio, fim);
        assertEquals(1, agregado.size());
        assertEquals(0, new BigDecimal("1").compareTo(agregado.getFirst().quantidade()));
        assertEquals(0, new BigDecimal("20").compareTo(agregado.getFirst().valorPrejuizo()));
        var consumoDia = relatorios.consumoPorDiaSemana(inicio, fim);
        assertEquals(7, consumoDia.size());
        var comConsumo = consumoDia.stream().filter(d -> d.quantidadeTotal().signum() > 0).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2").compareTo(comConsumo.quantidadeTotal()));
        var somaMensal = relatorios.cmvPorMes(inicio, fim).stream().map(CmvMensalDTO::cmv).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("60").compareTo(somaMensal));
    }

    @Test
    void reposicaoSugeridaUsaConsumoELeadTime() {
        var par = produtos.encontrar(produtoId).getParametro();
        par.setTempoReposicaoDias(20);
        par.setConsumoMedioDiario(new BigDecimal("1"));
        par.setEstoqueMinimo(new BigDecimal("5"));
        configRepository.flush();
        var sugestoes = relatorios.reposicaoSugerida(null);
        assertEquals(1, sugestoes.size());
        assertEquals(0, new BigDecimal("10").compareTo(sugestoes.getFirst().saldoAtual()));
        assertEquals(0, new BigDecimal("10").compareTo(sugestoes.getFirst().quantidadeSugerida()));
        assertEquals(20, sugestoes.getFirst().diasReposicao());
    }

    private long contarAbertos(TipoAlerta tipo) {
        return alertaRepository.findAllByAtivoTrueOrderByDataGeracaoDesc().stream()
                .filter(a -> !a.isVisualizado() && a.getTipo() == tipo).count();
    }
}