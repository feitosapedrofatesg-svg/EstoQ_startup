package com.estoq;

import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.business.balancos.*;
import com.estoq.business.categorias.ICategoriaRepository;
import com.estoq.business.consumos.*;
import com.estoq.business.produtosAbertos.*;
import java.util.List;
import java.util.UUID;
import com.estoq.business.entradas.*;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.movimentacoesEstoque.IMovimentacaoEstoqueRepository;
import com.estoq.business.movimentacoesEstoque.TipoMovimentacao;
import com.estoq.business.produtos.*;
import com.estoq.business.registro.*;
import com.estoq.business.sincronizacao.*;
import com.estoq.business.usuarios.*;
import com.estoq.core.exceptions.BaseException;
import com.estoq.core.exceptions.ConflictException;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL e servidor HTTP reais: concorrência de banco não é simulada por mocks. */
class SincronizacaoBalancoIntegrationTest {
    static EmbeddedPostgres pg;
    static ConfigurableApplicationContext app;
    static String origem;
    static final String SENHA = "Teste@12345";
    UsuarioPrincipal admin, cozinha;
    BalancoService balancos;
    BalancoDTO contado;
    Long produtoId;

    @BeforeAll static void iniciarServidor() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        app = new SpringApplicationBuilder(EstoqApplication.class).profiles("prod").run(
                "--server.port=0", "--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres") + "&currentSchema=sincronizacao",
                "--spring.datasource.username=postgres", "--spring.datasource.password=postgres",
                "--spring.flyway.schemas=sincronizacao", "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.generate_statistics=true",
                "--spring.jpa.properties.hibernate.session.events.log=false",
                "--server.servlet.session.cookie.secure=false", "--estoq.cors.origins=");
        origem = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll static void encerrarServidor() throws Exception {
        if (app != null) {
            app.getBean(SincronizacaoService.class).encerrarServidor();
            app.close();
        }
        if (pg != null) pg.close();
    }

    UsuarioPrincipal restaurante(String prefixo) {
        SecurityContextHolder.clearContext();
        String email = prefixo + System.nanoTime() + "@teste.local";
        app.getBean(RegistroService.class).registrar(new RegistroRequestDTO("Restaurante de teste", "Admin", email, SENHA));
        return UsuarioPrincipal.of(app.getBean(IUsuarioRepository.class).findByEmailIgnoreCaseAndAtivoTrue(email).orElseThrow());
    }

    static void autenticar(UsuarioPrincipal p) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
    }

    @BeforeEach void preparar() {
        admin = restaurante("admin"); autenticar(admin);
        var dto = new UsuarioCreateDTO("Cozinha", "cozinha" + System.nanoTime() + "@teste.local", SENHA, Perfil.COZINHA);
        var usuario = app.getBean(UsuarioService.class).criar(dto);
        cozinha = UsuarioPrincipal.of(app.getBean(IUsuarioRepository.class).findById(usuario.id()).orElseThrow());
        var produto = new ProdutoDTO(); produto.setNome("Arroz de teste"); produto.setUnidadeMedida(UnidadeMedida.KG);
        produto.setCategoriaId(app.getBean(ICategoriaRepository.class).findAllByAtivoTrue().getFirst().getId());
        produtoId = app.getBean(ProdutoService.class).criar(produto).getId();
        app.getBean(EntradaService.class).registrarEntrada(new EntradaRequestDTO(produtoId, new BigDecimal("10"), new BigDecimal("100"),
                UnidadeMedida.KG, LocalDate.now().plusDays(30), null, false));
        balancos = app.getBean(BalancoService.class);
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var iniciado = balancos.iniciar(criado.id(), new VersaoBalancoRequestDTO(criado.version()));
        var item = iniciado.itens().getFirst();
        balancos.registrarContagem(iniciado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("8"), item.version()));
        contado = balancos.buscar(iniciado.id());
    }

    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    ConfirmarBalancoRequestDTO confirmacao(BalancoDTO b) {
        return new ConfirmarBalancoRequestDTO(b.version(), b.itens().stream()
                .map(i -> new ConfirmarBalancoRequestDTO.ItemVersion(i.id(), i.version())).toList());
    }

    TransactionTemplate transacao() { return new TransactionTemplate(app.getBean(PlatformTransactionManager.class)); }

    static void aguardar(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "Operação concorrente não avançou"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    Callable<Object> executar(UsuarioPrincipal p, Supplier<?> acao) {
        return () -> {
            autenticar(p);
            try { return acao.get(); }
            catch (ConflictException e) { return e; }
            finally { SecurityContextHolder.clearContext(); }
        };
    }

    @Test void conclusaoBloqueiaContagemQueChegaAntesDoCommit() throws Exception {
        var pronto = new CountDownLatch(1); var liberar = new CountDownLatch(1);
        var item = contado.itens().getFirst();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var fechar = executor.submit(executar(admin, () -> transacao().execute(status -> {
                var concluido = balancos.confirmar(contado.id(), confirmacao(contado));
                pronto.countDown(); aguardar(liberar); return concluido;
            })));
            aguardar(pronto);
            var editar = executor.submit(executar(cozinha, () -> balancos.registrarContagem(contado.id(), item.id(),
                    new ContagemRequestDTO(new BigDecimal("9"), item.version()))));
            liberar.countDown();
            assertEquals(StatusBalanco.CONCLUIDO, ((BalancoDTO) fechar.get(10, TimeUnit.SECONDS)).status());
            assertInstanceOf(ConflictException.class, editar.get(10, TimeUnit.SECONDS));
        }
        var finalizado = balancos.buscar(contado.id());
        assertEquals(0, new BigDecimal("8").compareTo(finalizado.itens().getFirst().quantidadeFisica()));
    }

    @Test void contagemBloqueiaConclusaoQueUsaVersaoAntiga() throws Exception {
        var pronto = new CountDownLatch(1); var liberar = new CountDownLatch(1);
        var item = contado.itens().getFirst();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var editar = executor.submit(executar(cozinha, () -> transacao().execute(status -> {
                var alterado = balancos.registrarContagem(contado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("9"), item.version()));
                pronto.countDown(); aguardar(liberar); return alterado;
            })));
            aguardar(pronto);
            var fechar = executor.submit(executar(admin, () -> balancos.confirmar(contado.id(), confirmacao(contado))));
            liberar.countDown();
            assertInstanceOf(ItemBalancoDTO.class, editar.get(10, TimeUnit.SECONDS));
            assertInstanceOf(ConflictException.class, fechar.get(10, TimeUnit.SECONDS));
        }
        assertEquals(StatusBalanco.EM_ANDAMENTO, balancos.buscar(contado.id()).status());
    }

    @Test void duasContagensComMesmaVersaoNaoSeSobrescrevem() throws Exception {
        var largada = new CountDownLatch(1); var item = contado.itens().getFirst();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(executar(admin, () -> { aguardar(largada); return balancos.registrarContagem(contado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("9"), item.version())); }));
            var b = executor.submit(executar(cozinha, () -> { aguardar(largada); return balancos.registrarContagem(contado.id(), item.id(), new ContagemRequestDTO(new BigDecimal("7"), item.version())); }));
            largada.countDown();
            var resultadoA = a.get(10, TimeUnit.SECONDS); var resultadoB = b.get(10, TimeUnit.SECONDS);
            assertTrue((resultadoA instanceof ItemBalancoDTO && resultadoB instanceof ConflictException)
                    || (resultadoB instanceof ItemBalancoDTO && resultadoA instanceof ConflictException));
        }
    }

    @Test void balancoCom186ProdutosNaoFazUmaConsultaPorProduto() {
        var em = app.getBean(jakarta.persistence.EntityManager.class);
        transacao().executeWithoutResult(status -> {
            var categoria = app.getBean(ICategoriaRepository.class).findAllByAtivoTrue().getFirst();
            for (int i = 0; i < 185; i++) {
                var p = new ProdutoModel(); p.setNome("Produto de carga " + i);
                p.setCategoria(categoria); p.setUnidadeMedida(UnidadeMedida.KG); em.persist(p);
            }
        });
        var estatisticas = app.getBean(jakarta.persistence.EntityManagerFactory.class)
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        estatisticas.clear();
        var iniciado = balancos.iniciar(criado.id(), new VersaoBalancoRequestDTO(criado.version()));
        assertEquals(186, iniciado.itens().size());
        assertTrue(estatisticas.getPrepareStatementCount() < 20, "Iniciar deve criar os itens e saldos em lote.");
        estatisticas.clear();
        balancos.listar();
        assertTrue(estatisticas.getPrepareStatementCount() < 10, "Listar não deve consultar parâmetros por produto.");
        transacao().executeWithoutResult(status -> em.createNativeQuery(
                "update itens_balanco set quantidade_fisica=quantidade_sistema where balanco_id=:id and restaurante_id=:restaurante")
                .setParameter("id", iniciado.id()).setParameter("restaurante", admin.restauranteId()).executeUpdate());
        var atualizado = balancos.buscar(iniciado.id());
        estatisticas.clear();
        var concluido = balancos.confirmar(atualizado.id(), confirmacao(atualizado));
        assertTrue(estatisticas.getPrepareStatementCount() < 20, "Concluir deve carregar os relacionamentos juntos após bloquear.");
        estatisticas.clear();
        balancos.gerarAjustes(concluido.id(), new VersaoBalancoRequestDTO(concluido.version()));
        assertTrue(estatisticas.getPrepareStatementCount() < 20, "Ajustes sem diferença não devem apagar alertas por produto.");
        var novo = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
        var divergente = balancos.iniciar(novo.id(), new VersaoBalancoRequestDTO(novo.version()));
        transacao().executeWithoutResult(status -> em.createNativeQuery(
                "update itens_balanco set quantidade_fisica=quantidade_sistema+1 where balanco_id=:id and restaurante_id=:restaurante")
                .setParameter("id", divergente.id()).setParameter("restaurante", admin.restauranteId()).executeUpdate());
        var conferir = balancos.buscar(divergente.id());
        estatisticas.clear();
        balancos.confirmar(conferir.id(), confirmacao(conferir));
        assertTrue(estatisticas.getPrepareStatementCount() < 20, "186 diferenças devem criar seus alertas em lote.");
        estatisticas.clear();
        assertEquals(186, app.getBean(com.estoq.business.alertas.IAlertaRepository.class)
                .findAllByAtivoTrueOrderByDataGeracaoDesc().stream()
                .filter(a -> a.getTipo() == com.estoq.business.alertas.TipoAlerta.DIFERENCA_ESTOQUE).count());
        assertTrue(estatisticas.getPrepareStatementCount() < 5, "Ler alertas não deve carregar parâmetros por produto.");
    }

    @Test void retratoEmLoteRespeitaCategoriasDoBalancoParcial() {
        var categorias = app.getBean(ICategoriaRepository.class).findAllByAtivoTrue();
        var extra = new ProdutoDTO(); extra.setNome("Produto de outra categoria");
        extra.setCategoriaId(categorias.get(1).getId()); extra.setUnidadeMedida(UnidadeMedida.KG);
        app.getBean(ProdutoService.class).criar(extra);
        var criado = balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.PARCIAL,
                List.of(categorias.getFirst().getId())));
        var iniciado = balancos.iniciar(criado.id(), new VersaoBalancoRequestDTO(criado.version()));
        assertEquals(1, iniciado.itens().size());
        assertEquals(produtoId, iniciado.itens().getFirst().produtoId());
        assertEquals(0, new BigDecimal("10").compareTo(iniciado.itens().getFirst().quantidadeSistema()));
    }

    @Test void ajustesConcorrentesGeramUmaUnicaMovimentacao() throws Exception {
        var concluido = balancos.confirmar(contado.id(), confirmacao(contado));
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(executar(admin, () -> { aguardar(largada); return balancos.gerarAjustes(concluido.id(), new VersaoBalancoRequestDTO(concluido.version())); }));
            var b = executor.submit(executar(admin, () -> { aguardar(largada); return balancos.gerarAjustes(concluido.id(), new VersaoBalancoRequestDTO(concluido.version())); }));
            largada.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1, app.getBean(IMovimentacaoEstoqueRepository.class).findAll().stream().filter(m -> m.getTipo() == TipoMovimentacao.AJUSTE).count());
        assertEquals(0, new BigDecimal("8").compareTo(app.getBean(ILoteRepository.class).saldo(produtoId)));
    }

    @Test void bloquearBalancoRespeitaRestauranteERejeitaVersoesAusentes() {
        assertThrows(com.estoq.core.exceptions.FieldValidationException.class, () -> balancos.registrarContagem(contado.id(), contado.itens().getFirst().id(), new ContagemRequestDTO(BigDecimal.ONE, null)));
        var outro = restaurante("outro"); autenticar(outro);
        var erro = assertThrows(BaseException.class, () -> balancos.registrarContagem(contado.id(), contado.itens().getFirst().id(), new ContagemRequestDTO(BigDecimal.ONE, 0L)));
        assertEquals(404, erro.getHttpStatus().value());
    }

    HttpClient login(UsuarioPrincipal p) throws Exception {
        var cliente = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        var resposta = post(cliente, "/api/auth/login", "{\"email\":\"" + p.email() + "\",\"senha\":\"" + SENHA + "\"}");
        assertEquals(200, resposta.statusCode()); return cliente;
    }

    @Test void consumoConjuntoReverteTodosOsItensQuandoUmSaldoInsuficiente() {
        var extra = new ProdutoDTO(); extra.setNome("Produto sem saldo"); extra.setUnidadeMedida(UnidadeMedida.UN);
        extra.setCategoriaId(app.getBean(ICategoriaRepository.class).findAllByAtivoTrue().getFirst().getId());
        var idExtra = app.getBean(ProdutoService.class).criar(extra).getId();
        var pedido = new ConsumoLoteRequestDTO(UUID.randomUUID(), List.of(
                new ConsumoRequestDTO(produtoId, null, BigDecimal.ONE, null, null),
                new ConsumoRequestDTO(idExtra, null, BigDecimal.ONE, null, null)));
        var erro = assertThrows(ConflictException.class, () -> app.getBean(ConsumoLoteService.class).registrar(pedido));
        assertTrue(erro.getMessage().contains("Produto sem saldo"));
        assertEquals(0, BigDecimal.TEN.compareTo(app.getBean(ILoteRepository.class).saldo(produtoId)));
        assertEquals(0, app.getBean(IMovimentacaoEstoqueRepository.class).findAll().stream().filter(m -> m.getTipo() == TipoMovimentacao.CONSUMO).count());
        assertEquals(0, app.getBean(IOperacaoConsumoRepository.class).count());
    }

    @Test void cozinhaRegistraConjuntoViaHttpEReenvioNaoDuplica() throws Exception {
        var cliente = login(cozinha);
        var chave = UUID.randomUUID();
        String pedido = "{\"chave\":\"" + chave + "\",\"itens\":[{\"produtoId\":" + produtoId + ",\"quantidade\":0.125}]}";
        var primeira = post(cliente, "/api/consumos/lote", pedido);
        assertEquals(200, primeira.statusCode(), primeira.body());
        assertTrue(primeira.body().contains("\"unidadeMedida\":\"KG\""));
        var repetida = post(cliente, "/api/consumos/lote", pedido);
        assertEquals(200, repetida.statusCode()); assertEquals(primeira.body(), repetida.body());
        assertEquals(0, new BigDecimal("9.875").compareTo(app.getBean(ILoteRepository.class).saldo(produtoId)));
        assertEquals(409, post(cliente, "/api/consumos/lote", pedido.replace("0.125", "0.250")).statusCode());
        assertEquals(400, post(cliente, "/api/consumos/lote", pedido.replace("0.125", "0")).statusCode());
    }

    @Test void confirmacoesConjuntasConcorrentesBaixamUmaVez() throws Exception {
        var pedido = new ConsumoLoteRequestDTO(UUID.randomUUID(), List.of(
                new ConsumoRequestDTO(produtoId, null, BigDecimal.ONE, null, null)));
        var service = app.getBean(ConsumoLoteService.class);
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(executar(cozinha, () -> { aguardar(largada); return service.registrar(pedido); }));
            var b = executor.submit(executar(cozinha, () -> { aguardar(largada); return service.registrar(pedido); }));
            largada.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(0, new BigDecimal("9").compareTo(app.getBean(ILoteRepository.class).saldo(produtoId)));
        assertEquals(1, app.getBean(IOperacaoConsumoRepository.class).count());
    }

    @Test void consumoConjuntoMantemEmbalagensCoerentesERejeitaLoteVencido() {
        var lote = app.getBean(ILoteRepository.class).findParaConsumoEmLote(List.of(produtoId)).getFirst();
        var embalagem = app.getBean(ProdutoAbertoService.class).abrirEmbalagem(
                new AbrirProdutoRequestDTO(produtoId, lote.getId(), new BigDecimal("2"), BigDecimal.ZERO, lote.getVersion(), null));
        var service = app.getBean(ConsumoLoteService.class);
        service.registrar(new ConsumoLoteRequestDTO(UUID.randomUUID(), List.of(
                new ConsumoRequestDTO(produtoId, null, new BigDecimal("3"), null, null))));
        assertTrue(app.getBean(ProdutoAbertoService.class).buscar(embalagem.id()).finalizado());
        assertEquals(0, new BigDecimal("7").compareTo(app.getBean(ILoteRepository.class).saldo(produtoId)));
        transacao().executeWithoutResult(s -> app.getBean(ILoteRepository.class).findById(lote.getId()).orElseThrow().setDataValidade(LocalDate.now().minusDays(1)));
        assertThrows(ConflictException.class, () -> service.registrar(new ConsumoLoteRequestDTO(UUID.randomUUID(), List.of(
                new ConsumoRequestDTO(produtoId, lote.getId(), BigDecimal.ONE, null, null)))));
        assertEquals(0, new BigDecimal("7").compareTo(app.getBean(ILoteRepository.class).saldo(produtoId)));
    }

    @Test void chaveDeConsumoNaoVazaRespostaEntreRestaurantes() {
        var chave = UUID.randomUUID();
        var service = app.getBean(ConsumoLoteService.class);
        service.registrar(new ConsumoLoteRequestDTO(chave, List.of(
                new ConsumoRequestDTO(produtoId, null, BigDecimal.ONE, null, null))));
        var outro = restaurante("consumoIsolado"); autenticar(outro);
        assertThrows(BaseException.class, () -> service.registrar(new ConsumoLoteRequestDTO(chave, List.of(
                new ConsumoRequestDTO(produtoId, null, BigDecimal.ONE, null, null)))));
        assertEquals(0, app.getBean(IOperacaoConsumoRepository.class).count());
        autenticar(admin);
        assertEquals(0, new BigDecimal("9").compareTo(app.getBean(ILoteRepository.class).saldo(produtoId)));
    }

    HttpResponse<String> post(HttpClient cliente, String path, String json) throws Exception {
        var csrf = cliente.send(HttpRequest.newBuilder(URI.create(origem + "/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        var matcher = java.util.regex.Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"").matcher(csrf);
        assertTrue(matcher.find());
        return cliente.send(HttpRequest.newBuilder(URI.create(origem + path)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", matcher.group(1)).POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    class Canal implements AutoCloseable {
        final BlockingQueue<String> avisos = new LinkedBlockingQueue<>();
        final InputStream entrada;
        Canal(HttpClient cliente) throws Exception {
            var resposta = cliente.send(HttpRequest.newBuilder(URI.create(origem + "/api/sincronizacao/eventos")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, resposta.statusCode()); entrada = resposta.body();
            Thread.ofVirtual().start(() -> {
                try (var leitor = new BufferedReader(new InputStreamReader(entrada))) {
                    String evento = "", linha;
                    while ((linha = leitor.readLine()) != null) {
                        if (linha.startsWith("event:")) evento = linha.substring(6);
                        else if (linha.startsWith("data:") && !evento.equals("heartbeat")) avisos.add(evento + " " + linha.substring(5));
                    }
                } catch (Exception ignorado) { /* Fechamento esperado. */ }
                finally { avisos.add("fim"); }
            });
            assertTrue(avisos.poll(5, TimeUnit.SECONDS).startsWith("conectado"));
        }
        @Override public void close() throws Exception { entrada.close(); }
    }

    @Test void eventosChegamAOutraSessaoSomenteAposCommitESemVazarRestaurante() throws Exception {
        var outro = restaurante("isolado"); autenticar(admin);
        try (var a = new Canal(login(admin)); var b = new Canal(login(outro))) {
            transacao().executeWithoutResult(status -> {
                var notificador = app.getBean(DadosAlteradosService.class);
                notificador.registrar(admin.restauranteId(), GrupoDados.BALANCOS);
                notificador.registrar(admin.restauranteId(), GrupoDados.ESTOQUE);
                assertNull(a.avisos.poll());
            });
            var aviso = a.avisos.poll(5, TimeUnit.SECONDS);
            assertNotNull(aviso); assertTrue(aviso.contains("BALANCOS") && aviso.contains("ESTOQUE"));
            assertFalse(aviso.contains("restauranteId"));
            assertNull(b.avisos.poll(500, TimeUnit.MILLISECONDS));
            transacao().executeWithoutResult(status -> {
                balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL)); status.setRollbackOnly();
            });
            assertNull(a.avisos.poll(500, TimeUnit.MILLISECONDS));
            // Uma gravação real da cozinha deve avisar a sessão do admin.
            autenticar(cozinha);
            balancos.criar(new CriarBalancoRequestDTO(TipoBalanco.GERAL));
            assertTrue(a.avisos.poll(5, TimeUnit.SECONDS).contains("BALANCOS"));
            assertNull(b.avisos.poll(500, TimeUnit.MILLISECONDS));
        }
    }

    @Test void logoutEncerraCanalEAnonimoNaoConecta() throws Exception {
        var anonimo = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(origem + "/api/sincronizacao/eventos")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonimo.statusCode());
        var cliente = login(cozinha);
        try (var canal = new Canal(cliente)) {
            assertEquals(204, post(cliente, "/api/auth/logout", "{}").statusCode());
            assertEquals("fim", canal.avisos.poll(5, TimeUnit.SECONDS));
        }
    }

    @Test void revogacaoDaContaEncerraCanalAberto() throws Exception {
        try (var canal = new Canal(login(cozinha))) {
            app.getBean(UsuarioService.class).desativar(cozinha.id());
            app.getBean(SincronizacaoService.class).heartbeat();
            assertEquals("fim", canal.avisos.poll(5, TimeUnit.SECONDS));
        }
    }
}
