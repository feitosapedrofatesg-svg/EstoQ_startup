package com.estoq;

import static com.estoq.core.helpers.HorarioOperacao.FUSO;

import com.estoq.business.balancos.BalancoService;
import com.estoq.business.balancos.VersaoBalancoRequestDTO;
import com.estoq.business.balancos.ConfirmarBalancoRequestDTO;
import com.estoq.business.balancos.ContagemRequestDTO;
import com.estoq.business.balancos.CriarBalancoRequestDTO;
import com.estoq.business.balancos.StatusBalanco;
import com.estoq.business.balancos.TipoBalanco;
import com.estoq.business.alertas.AlertaService;
import com.estoq.business.alertas.AlertaModel;
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
import com.estoq.business.dashboard.DashboardService;
import com.estoq.business.entradas.EntradaRequestDTO;
import com.estoq.business.entradas.EntradaService;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.movimentacoesEstoque.IMovimentacaoEstoqueRepository;
import com.estoq.business.movimentacoesEstoque.TipoMovimentacao;
import com.estoq.business.movimentacoesEstoque.MovimentacaoConsultaService;
import com.estoq.business.parametrosCmv.ParametroCmvDTO;
import com.estoq.business.parametrosCmv.ParametroCmvService;
import com.estoq.business.produtos.ProdutoDTO;
import com.estoq.business.produtos.ProdutoService;
import com.estoq.business.produtos.UnidadeMedida;
import com.estoq.business.relatorios.CmvMensalDTO;
import com.estoq.business.relatorios.CmvReceitaBaseDTO;
import com.estoq.business.relatorios.CmvReceitaBaseService;
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
    MovimentacaoConsultaService consultas;
    @Autowired
    RelatorioService relatorios;
    @Autowired
    DashboardService dashboard;
    @Autowired
    CmvReceitaBaseService receitasCmv;
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

    private VersaoBalancoRequestDTO versao(Long id) {
        return new VersaoBalancoRequestDTO(balancos.buscar(id).version());
    }

    private ConfirmarBalancoRequestDTO confirmacao(Long id) {
        var b = balancos.buscar(id);
        return new ConfirmarBalancoRequestDTO(b.version(), b.itens().stream()
                .map(i -> new ConfirmarBalancoRequestDTO.ItemVersion(i.id(), i.version())).toList());
    }

    @BeforeEach
    void preparar() {
        var u = new UsuarioModel();
        u.setNome("Gerente");
        u.setEmail("gerente@test.local");
        u.setSenha("hash");
        u.setPerfil(Perfil.ADMIN);
        users.saveAndFlush(u);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u.getEmail(), null, List.of()));
        var c = new CategoriaModel();
        c.setNome("Categoria");
        categorias.saveAndFlush(c);
        var d = new ProdutoDTO();
        d.setNome("Açúcar");
        d.setCategoriaId(c.getId());
        d.setUnidadeMedida(UnidadeMedida.KG);
        produtoId = produtos.criar(d).getId();
        entradas.registrarEntrada(new EntradaRequestDTO(produtoId, new BigDecimal("10"), new BigDecimal("200"),
                UnidadeMedida.KG, LocalDate.now(FUSO).plusDays(60), null, false));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void balancoEuSerumDebitoDistribuiFifo() {
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id(), versao(criado.id()));
        assertEquals(StatusBalanco.EM_ANDAMENTO, iniciado.status());
        var item = iniciado.itens().getFirst();
        assertEquals(0, new BigDecimal("10").compareTo(item.quantidadeSistema()));
        var contado = balancos.registrarContagem(iniciado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("8"), item.version()));
        assertEquals(0, new BigDecimal("8").compareTo(contado.quantidadeFisica()));
        var confirmado = balancos.confirmar(iniciado.id(), confirmacao(iniciado.id()));
        assertEquals(StatusBalanco.CONCLUIDO, confirmado.status());
        var ajustado = balancos.gerarAjustes(confirmado.id(), versao(confirmado.id()));
        assertTrue(ajustado.itens().getFirst().ajusteAplicado());
        assertEquals(0, new BigDecimal("2").compareTo(ajustado.itens().getFirst().diferenca().abs()));
        assertEquals(0, new BigDecimal("8").compareTo(lotes.saldo(produtoId)));
        var ajustes = movimentos.findAll().stream().filter(m -> m.getTipo() == TipoMovimentacao.AJUSTE).toList();
        assertEquals(1, ajustes.size());
        assertEquals(0, new BigDecimal("2").compareTo(ajustes.getFirst().getDelta().abs()));
        // Reaplicar o balanço não baixa outra vez nem duplica a perda.
        balancos.gerarAjustes(confirmado.id(), versao(confirmado.id()));
        assertEquals(0, new BigDecimal("8").compareTo(lotes.saldo(produtoId)));
        var fim = LocalDateTime.now().plusDays(1);
        var inicio = fim.minusDays(30);
        var perdas = consultas.listar(produtoId, null, null, TipoMovimentacao.DESPERDICIO, inicio, fim);
        assertEquals(1, perdas.size());
        assertEquals("Diferença negativa de balanço", perdas.getFirst().descricaoMotivo());
        assertEquals(0, new BigDecimal("40").compareTo(perdas.getFirst().valorPrejuizo()));
        assertEquals(0, new BigDecimal("2").compareTo(relatorios.desperdicio(inicio, fim).getFirst().quantidade()));
        assertEquals(0, new BigDecimal("40").compareTo(relatorios.desperdicioAgregado(inicio, fim).getFirst().valorPrejuizo()));
        var cmv = relatorios.calcularCmv(inicio, fim, null);
        assertEquals(0, new BigDecimal("40").compareTo(cmv.valorDesperdicio()));
        assertEquals(0, BigDecimal.ZERO.compareTo(cmv.valorPerdasNaoExplicadas()));
        assertEquals(0, new BigDecimal("40").compareTo(dashboard.resumo(inicio, fim, null).valorDesperdicioPeriodo()));
        var perdaMensal = relatorios.cmvPorMes(inicio, fim).stream()
                .map(CmvMensalDTO::valorDesperdicio).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("40").compareTo(perdaMensal));
        assertTrue(consultas.listar(-1L, null, null, TipoMovimentacao.DESPERDICIO, inicio, fim).isEmpty());
    }

    @Test
    void balancoSuperavitSomaNoLoteDisponivel() {
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id(), versao(criado.id()));
        var item = iniciado.itens().getFirst();
        balancos.registrarContagem(iniciado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("12"), item.version()));
        var confirmado = balancos.confirmar(iniciado.id(), confirmacao(iniciado.id()));
        balancos.gerarAjustes(confirmado.id(), versao(confirmado.id()));
        assertEquals(0, new BigDecimal("12").compareTo(lotes.saldo(produtoId)));
        var fim = LocalDateTime.now().plusDays(1);
        assertTrue(consultas.listar(produtoId, null, null, TipoMovimentacao.DESPERDICIO,
                fim.minusDays(30), fim).isEmpty());
        assertTrue(relatorios.desperdicio(fim.minusDays(30), fim).isEmpty());
        var ajustes = movimentos.findAll().stream().filter(m -> m.getTipo() == TipoMovimentacao.AJUSTE).toList();
        assertEquals(1, ajustes.size());
        assertEquals(0, new BigDecimal("2").compareTo(ajustes.getFirst().getDelta().abs()));
    }

    @Test
    void naoConfirmaSemContagem() {
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id(), versao(criado.id()));
        assertThrows(ConflictException.class, () -> balancos.confirmar(iniciado.id(), confirmacao(iniciado.id())));
    }

    @Test
    void cmvUsaMovimentacoesELote() {
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, new BigDecimal("2"), null, null));
        desperdicios.registrarDesperdicio(new DesperdicioRequestDTO(produtoId, null, new BigDecimal("1"),
                MotivoDesperdicio.DETERIORACAO, null, null, null));
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
    void cmvHistoricoNaoMudaComConsumoPosteriorEProdutoDesativado() {
        // Janeiro no fuso operacional começa às 03h UTC.
        var inicio = LocalDateTime.of(2026, 1, 1, 3, 0);
        var fim = inicio.plusMonths(1);
        movimentos.findAll().forEach(m -> m.setDataHora(inicio));
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, new BigDecimal("2"), null, null));
        movimentos.findAll().stream().filter(m -> m instanceof com.estoq.business.consumos.ConsumoModel)
                .forEach(m -> m.setDataHora(inicio.plusDays(10)));
        movimentos.flush();
        var antes = relatorios.calcularCmv(inicio, fim, null);
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, new BigDecimal("8"), null, null));
        movimentos.findAll().stream().filter(m -> m.getQuantidade().compareTo(new BigDecimal("8")) == 0)
                .forEach(m -> m.setDataHora(fim.plusDays(10)));
        produtos.excluir(produtoId);
        movimentos.flush();
        var depois = relatorios.calcularCmv(inicio, fim, null);
        assertEquals(0, antes.cmv().compareTo(depois.cmv()));
        assertEquals(0, new BigDecimal("40").compareTo(depois.cmv()));
        assertEquals(0, new BigDecimal("160").compareTo(depois.valorEstoqueFinal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(depois.valorEstoqueInicial()));
        assertEquals(1, relatorios.cmvPorMes(inicio, fim).size());
        assertEquals(0, depois.cmv().compareTo(relatorios.cmvPorMes(inicio, fim).getFirst().cmv()));
    }

    @Test
    void cmvConservaPrecisaoDeQuantidadeEPreco() {
        var lote = lotes.listarEstoque().getFirst();
        lote.setQuantidadeAtual(new BigDecimal("0.001"));
        lote.setPrecoUnitario(new BigDecimal("123.456789"));
        movimentos.findAll().forEach(m -> {
            m.setQuantidade(new BigDecimal("0.001"));
            m.setQuantidadePosterior(new BigDecimal("0.001"));
            m.setDataHora(LocalDateTime.of(2026, 1, 1, 0, 0));
        });
        movimentos.flush();
        var resumo = relatorios.calcularCmv(LocalDateTime.of(2026, 2, 1, 0, 0), LocalDateTime.of(2026, 3, 1, 0, 0), null);
        assertEquals(0, new BigDecimal("0.12").compareTo(resumo.valorEstoqueInicial()));
        assertEquals(0, new BigDecimal("0.12").compareTo(resumo.valorEstoqueFinal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resumo.cmv()));
    }

    @Test
    void cmvMensalRespeitaMesesIncompletosEAjustesPositivos() {
        var inicio = LocalDateTime.of(2026, 1, 15, 0, 0);
        movimentos.findAll().forEach(m -> m.setDataHora(inicio.minusDays(10)));
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id(), versao(criado.id()));
        balancos.registrarContagem(iniciado.id(), iniciado.itens().getFirst().id(), new ContagemRequestDTO(new BigDecimal("12"), iniciado.itens().getFirst().version()));
        balancos.confirmar(iniciado.id(), confirmacao(iniciado.id()));
        balancos.gerarAjustes(iniciado.id(), versao(iniciado.id()));
        movimentos.findAll().stream().filter(m -> m instanceof com.estoq.business.ajustes.AjusteModel)
                .forEach(m -> m.setDataHora(inicio.plusDays(2)));
        movimentos.flush();
        var fim = LocalDateTime.of(2026, 2, 1, 0, 0);
        var mensal = relatorios.cmvPorMes(inicio, fim);
        assertEquals(1, mensal.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(mensal.getFirst().valorCompras()));
        assertEquals(0, new BigDecimal("-40").compareTo(mensal.getFirst().cmv()));
        assertThrows(FieldValidationException.class, () -> relatorios.calcularCmv(fim, inicio, null));
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
    void receitaBaseCmvPermaneceSalvaPorPeriodo() {
        var inicio = LocalDate.of(2026, 9, 1);
        var fim = LocalDate.of(2026, 9, 30);
        assertNull(receitasCmv.obter(inicio, fim).receitaBase());

        receitasCmv.salvar(new CmvReceitaBaseDTO(inicio, fim, new BigDecimal("12500.50")));

        assertEquals(0, new BigDecimal("12500.50").compareTo(receitasCmv.obter(inicio, fim).receitaBase()));
        assertNull(receitasCmv.obter(inicio, fim.minusDays(1)).receitaBase());
    }

    @Test
    void resumoDaVisaoGeralUsaReceitaBaseSalvaNoMesmoPeriodo() {
        var inicio = LocalDate.now(FUSO).minusDays(30);
        var fim = LocalDate.now(FUSO);
        receitasCmv.salvar(new CmvReceitaBaseDTO(inicio, fim, new BigDecimal("100")));

        var horario = new com.estoq.core.helpers.HorarioOperacao();
        var resumo = dashboard.resumo(horario.inicioUtc(inicio), horario.inicioUtc(fim.plusDays(1)), null);

        assertEquals(0, BigDecimal.ZERO.compareTo(resumo.cmvPercentual()));
    }

    @Test
    void faturamentoDiarioAlimentaMesmoPercentualNoRelatorioENoDashboard() {
        var hoje = LocalDate.now(FUSO);
        var horario = new com.estoq.core.helpers.HorarioOperacao();
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, new BigDecimal("2"), null, null));
        desperdicios.registrarDesperdicio(new DesperdicioRequestDTO(produtoId, null, BigDecimal.ONE,
                MotivoDesperdicio.DETERIORACAO, null, null, null));
        for (int i = 0; i < 3; i++) {
            receitasCmv.salvarDia(new com.estoq.business.relatorios.FaturamentoDiarioDTO(
                    hoje.minusDays(i), i == 1 ? BigDecimal.ZERO : new BigDecimal("100"), null));
        }
        var inicio = horario.inicioUtc(hoje.minusDays(2));
        var fim = horario.inicioUtc(hoje.plusDays(1));
        var relatorio = relatorios.calcularCmv(inicio, fim, null);
        var resumo = dashboard.resumo(inicio, fim, null);
        assertEquals(0, new BigDecimal("60").compareTo(relatorio.cmv()));
        assertEquals(0, new BigDecimal("200").compareTo(relatorio.receitaBase()));
        assertEquals(0, new BigDecimal("30").compareTo(relatorio.cmvPercentual()));
        assertEquals(relatorio.cmvPercentual(), resumo.cmvPercentual());
        assertEquals(0, resumo.diasFaturamentoPendentes());
        assertTrue(!resumo.receitaPorPeriodo());
        // Corrigir um dia substitui seu valor e impede gravar com uma versão antiga.
        var dia = receitasCmv.faturamento(hoje, hoje).dias().getFirst();
        receitasCmv.salvarDia(new com.estoq.business.relatorios.FaturamentoDiarioDTO(hoje, new BigDecimal("300"), dia.version()));
        assertThrows(ConflictException.class, () -> receitasCmv.salvarDia(dia));
        assertEquals(0, new BigDecimal("400").compareTo(receitasCmv.faturamento(hoje.minusDays(2), hoje).totalInformado()));
    }

    @Test
    void faturamentoAusenteEZeroTemSignificadosDiferentes() {
        var hoje = LocalDate.now(FUSO);
        var horario = new com.estoq.core.helpers.HorarioOperacao();
        assertEquals(1, receitasCmv.faturamento(hoje, hoje).diasPendentes());
        assertNull(receitasCmv.faturamento(hoje, hoje).receitaBase());
        var zero = receitasCmv.salvarDia(new com.estoq.business.relatorios.FaturamentoDiarioDTO(hoje, BigDecimal.ZERO, null));
        var periodo = receitasCmv.faturamento(hoje, hoje);
        assertEquals(0, periodo.diasPendentes());
        assertEquals(1, periodo.diasInformados());
        assertEquals(0, periodo.receitaBase().signum());
        assertNull(relatorios.calcularCmv(horario.inicioUtc(hoje), horario.inicioUtc(hoje.plusDays(1)), null).cmvPercentual());
        receitasCmv.salvarDia(new com.estoq.business.relatorios.FaturamentoDiarioDTO(hoje, null, zero.version()));
        assertEquals(1, receitasCmv.faturamento(hoje, hoje).diasPendentes());
        assertThrows(FieldValidationException.class, () -> receitasCmv.salvarDia(
                new com.estoq.business.relatorios.FaturamentoDiarioDTO(hoje, new BigDecimal("-1"), null)));
    }

    @Test
    void totaisLegadosNaoSaoDistribuidosNemSomadosAosDias() {
        var inicio = LocalDate.of(2026, 9, 10);
        var fim = inicio.plusDays(2);
        receitasCmv.salvar(new CmvReceitaBaseDTO(inicio, fim, new BigDecimal("1000")));
        var periodo = receitasCmv.faturamento(inicio, fim);
        assertTrue(periodo.receitaPorPeriodo());
        assertEquals(3, periodo.diasPendentes());
        assertEquals(0, periodo.totalInformado().signum());
        assertEquals(0, new BigDecimal("1000").compareTo(periodo.receitaBase()));
        assertNull(receitasCmv.faturamento(inicio, fim.minusDays(1)).receitaBase());
        receitasCmv.salvarDia(new com.estoq.business.relatorios.FaturamentoDiarioDTO(inicio, new BigDecimal("100"), null));
        assertTrue(receitasCmv.faturamento(inicio, fim).receitaPorPeriodo());
        receitasCmv.salvarDia(new com.estoq.business.relatorios.FaturamentoDiarioDTO(inicio.plusDays(1), new BigDecimal("200"), null));
        receitasCmv.salvarDia(new com.estoq.business.relatorios.FaturamentoDiarioDTO(fim, BigDecimal.ZERO, null));
        periodo = receitasCmv.faturamento(inicio, fim);
        assertTrue(!periodo.receitaPorPeriodo());
        assertEquals(0, new BigDecimal("300").compareTo(periodo.receitaBase()));
        assertEquals(0, new BigDecimal("1000").compareTo(receitasCmv.obter(inicio, fim).receitaBase()));
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
        var iniciado = balancos.iniciar(criado.id(), versao(criado.id()));
        var existente = iniciado.itens().stream().filter(i -> i.produtoId().equals(produtoId)).findFirst()
                .orElseThrow();
        var novo = iniciado.itens().stream().filter(i -> i.produtoId().equals(semEstoque)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0").compareTo(novo.quantidadeSistema()));
        balancos.registrarContagem(iniciado.id(), existente.id(),
                new ContagemRequestDTO(existente.quantidadeSistema(), existente.version()));
        balancos.registrarContagem(iniciado.id(), novo.id(), new ContagemRequestDTO(new BigDecimal("3"), novo.version()));
        var confirmado = balancos.confirmar(iniciado.id(), confirmacao(iniciado.id()));
        balancos.gerarAjustes(confirmado.id(), versao(confirmado.id()));
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
        guardado.setProximaExecucao(LocalDate.now(FUSO));
        configRepository.flush();
        alertas.avaliarAgendamentos();
        assertEquals(2, contarAbertos(TipoAlerta.BALANCO_PENDENTE));
        assertEquals(LocalDate.now(FUSO), configRepository.findByIdAndAtivoTrue(cfg.id()).orElseThrow().getProximaExecucao());
        alertas.avaliarAgendamentos();
        assertEquals(2, contarAbertos(TipoAlerta.BALANCO_PENDENTE));
        assertEquals(1, alertas.listar(false, Perfil.COZINHA).stream()
                .filter(a -> a.tipo() == TipoAlerta.BALANCO_PENDENTE).count());
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id(), versao(criado.id()));
        var item = iniciado.itens().getFirst();
        balancos.registrarContagem(iniciado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("8"), item.version()));
        var confirmado = balancos.confirmar(iniciado.id(), confirmacao(iniciado.id()));
        assertEquals(0, contarAbertos(TipoAlerta.BALANCO_PENDENTE));
        assertTrue(configRepository.findByIdAndAtivoTrue(cfg.id()).orElseThrow().getProximaExecucao().isAfter(LocalDate.now(FUSO)));
        assertEquals(1, contarAbertos(TipoAlerta.DIFERENCA_ESTOQUE));
        balancos.gerarAjustes(confirmado.id(), versao(confirmado.id()));
        assertEquals(0, contarAbertos(TipoAlerta.DIFERENCA_ESTOQUE));
    }

    @Test
    void agendamentoAvisaNoDiaPelaCentralSemDuplicarOuReabrirAvisoVisto() {
        var cfg = configuracoes.salvar(new ConfiguracaoBalancoDTO(null, null, PeriodicidadeBalanco.DIARIA, null, null));
        assertEquals(0, alertas.contarAbertos(Perfil.ADMIN));
        var guardado = configRepository.findByIdAndAtivoTrue(cfg.id()).orElseThrow();
        guardado.setProximaExecucao(LocalDate.now(FUSO));
        configRepository.flush();
        var avisos = alertas.listar(false, Perfil.ADMIN);
        assertEquals(1, avisos.size());
        assertTrue(avisos.getFirst().mensagem().contains(LocalDate.now(FUSO).format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        assertEquals(1, alertas.contarAbertos(Perfil.ADMIN));
        alertas.marcarVisualizado(avisos.getFirst().id());
        assertEquals(0, alertas.contarAbertos(Perfil.ADMIN));
        assertEquals(1, alertas.contarAbertos(Perfil.COZINHA));
        assertEquals(LocalDate.now(FUSO), configuracoes.listar().getFirst().proximaExecucao());
    }

    @Test
    void centralDeAlertasContaAbertosPorPerfilEAtualizaAoMarcarVisto() {
        var alertaAdmin = new AlertaModel();
        alertaAdmin.setTipo(TipoAlerta.BALANCO_PENDENTE);
        alertaAdmin.setPerfilDestino(Perfil.ADMIN);
        alertaAdmin.setMensagem("Alerta do administrador");
        alertaAdmin.setDataGeracao(LocalDateTime.now());
        alertaRepository.saveAndFlush(alertaAdmin);

        var alertaCozinha = new AlertaModel();
        alertaCozinha.setTipo(TipoAlerta.BALANCO_PENDENTE);
        alertaCozinha.setPerfilDestino(Perfil.COZINHA);
        alertaCozinha.setMensagem("Alerta da cozinha");
        alertaCozinha.setDataGeracao(LocalDateTime.now());
        alertaRepository.saveAndFlush(alertaCozinha);

        assertEquals(1, alertas.contarAbertos(Perfil.ADMIN));
        assertEquals(1, alertas.listar(false, Perfil.ADMIN).size());
        assertEquals(1, alertas.contarAbertos(Perfil.COZINHA));

        alertas.marcarVisualizado(alertaAdmin.getId());

        assertEquals(0, alertas.contarAbertos(Perfil.ADMIN));
        assertEquals(0, alertas.listar(false, Perfil.ADMIN).size());
        assertEquals(1, alertas.contarAbertos(Perfil.COZINHA));
    }

    @Test
    void relatoriosAgregadosDesperdicioConsumoECmvMensal() {
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, new BigDecimal("2"), null, null));
        desperdicios.registrarDesperdicio(new DesperdicioRequestDTO(produtoId, null, new BigDecimal("1"),
                MotivoDesperdicio.DETERIORACAO, null, null, null));
        var fim = LocalDateTime.now().plusDays(1);
        var inicio = fim.minusDays(30);
        var agregado = relatorios.desperdicioAgregado(inicio, fim);
        assertEquals(1, agregado.size());
        assertEquals(0, new BigDecimal("1").compareTo(agregado.getFirst().quantidade()));
        assertEquals(0, new BigDecimal("20").compareTo(agregado.getFirst().valorPrejuizo()));
        var consumoDia = relatorios.consumoPorDiaSemana(inicio, fim);
        assertEquals(7, consumoDia.size());
        var comConsumo = consumoDia.stream().filter(d -> d.valorTotal().signum() > 0).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("40").compareTo(comConsumo.valorTotal()));
        var somaMensal = relatorios.cmvPorMes(inicio, fim).stream().map(CmvMensalDTO::cmv).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        assertEquals(0, new BigDecimal("60").compareTo(somaMensal));
    }

    @Test
    void custoSemanalSomaReaisMesmoComProdutosEmUnidadesDiferentes() {
        var bebida = new ProdutoDTO(); bebida.setNome("Suco"); bebida.setUnidadeMedida(UnidadeMedida.L);
        bebida.setCategoriaId(produtos.encontrar(produtoId).getCategoria().getId());
        var bebidaId = produtos.criar(bebida).getId();
        entradas.registrarEntrada(new EntradaRequestDTO(bebidaId, BigDecimal.ONE, new BigDecimal("20"),
                UnidadeMedida.L, LocalDate.now(FUSO).plusDays(30), null, false));
        consumos.registrarConsumo(new ConsumoRequestDTO(produtoId, null, new BigDecimal("2"), null, null));
        consumos.registrarConsumo(new ConsumoRequestDTO(bebidaId, null, new BigDecimal("0.5"), null, null));
        movimentos.findAll().stream().filter(m -> m instanceof com.estoq.business.consumos.ConsumoModel)
                .forEach(m -> m.setDataHora(LocalDateTime.of(2026, 10, 18, 1, 0)));
        movimentos.flush();
        var dias = relatorios.consumoPorDiaSemana(LocalDateTime.of(2026, 10, 17, 3, 0), LocalDateTime.of(2026, 10, 18, 3, 0));
        assertEquals(1, dias.size());
        assertEquals(java.time.DayOfWeek.SATURDAY, dias.getFirst().diaSemana());
        assertEquals(0, new BigDecimal("50").compareTo(dias.getFirst().valorTotal()));
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
