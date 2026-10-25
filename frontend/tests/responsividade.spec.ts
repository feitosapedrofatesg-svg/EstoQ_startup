import { test, expect, type Page } from "@playwright/test";
import { preparar, abrirMenu } from "./fixtures";

const NOME = "Ingrediente com descrição completa, embalagem e identificação do fornecedor";
const cmv = { cmv: 123456.78, receitaBase: 350000, cmvPercentual: 35.27, percentualIdeal: 30,
  valorEstoqueInicial: 45000, valorCompras: 100000, valorEstoqueFinal: 21543.22,
  valorConsumoRegistrado: 115000, valorDesperdicio: 7000, percentualDesperdicioSobreCmv: 5.67,
  valorPerdasNaoExplicadas: 1456.78, valorAjustesPositivosBalancos: 12345.67, diferencaPercentualParaMeta: 5.27 };

async function prepararTelas(page: Page, perfil = "ADMIN") {
  await preparar(page, perfil);
  await page.addInitScript(() => {
    (window as any).EventSource = class extends EventTarget { close() {} };
  });
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    const json = (value: unknown) => route.fulfill({ json: value });
    if (path === "/api/auth/me") return json({ id: 1, nome: "Responsável", email: "teste@teste.com", perfil,
      restauranteId: perfil === "PLATAFORMA" ? null : 1,
      restauranteNome: "Restaurante com nome completo e identificação da unidade" });
    if (path === "/api/estoque") return json([{ produtoId: 1, produtoNome: NOME, categoriaNome: "Grãos e ingredientes",
      unidadeMedida: "KG", saldoAtual: 12345.678, saldoDisponivelConsumo: 12000,
      valorEstoque: 123456.78, estoqueMinimo: 10, estoqueMedio: 15, estoqueMaximo: 20,
      abaixoDoMinimo: false, possuiItensAbertos: true }]);
    if (path === "/api/lotes") return json([{ id: 1, version: 1, codigo: "LOTE-COM-IDENTIFICACAO-LONGA-DO-FORNECEDOR",
      produtoId: 1, produtoNome: NOME, unidadeMedida: "KG", quantidadeInicial: 15000,
      quantidadeAtual: 12345.678, dataEntrada: "2026-10-12", dataValidade: "2026-11-12", precoUnitario: 10 }]);
    if (path === "/api/movimentacoes") return json([{ id: 1, tipo: "DESPERDICIO", dataHora: "2026-10-12T19:26:00",
      produtoId: 1, produtoNome: NOME, loteCodigo: "LOTE-IDENTIFICADO", unidadeMedida: "KG", quantidade: 123.456,
      quantidadeAnterior: 13000, quantidadePosterior: 12876.544, delta: -123.456, valorPrejuizo: 1234.56,
      motivo: "DETERIORACAO", descricaoMotivo: "Deterioração", usuarioNome: "Responsável pela cozinha" }]);
    if (path === "/api/produtos-abertos") return json([{ id: 1, version: 1, produtoId: 1, produtoNome: NOME,
      unidadeMedida: "KG", loteId: 1, loteCodigo: "LOTE-IDENTIFICADO", dataValidade: "2026-11-12",
      dataAbertura: "2026-10-12T19:26:00", quantidadeAberta: 10, quantidadeUtilizada: 2,
      quantidadeRestante: 8, finalizado: false }]);
    if (path === "/api/balancos/proximo") return json([{ id: 1, version: 1, periodicidade: "MENSAL", diaExecucao: 15, proximaExecucao: "2026-10-15" }]);
    if (path === "/api/balancos") return json([{ id: 1, version: 1, tipo: "GERAL", status: "EM_ANDAMENTO",
      dataHora: "2026-10-12T19:26:00", usuarioNome: "Responsável pela cozinha", itens: [{ id: 1, version: 1,
        produtoId: 1, produtoNome: NOME, unidadeMedida: "KG", quantidadeSistema: 12345.678,
        quantidadeFisica: null, diferenca: null, ajusteAplicado: false }] }]);
    if (path === "/api/relatorios/cmv") return json(cmv);
    if (path === "/api/relatorios/cmv/receita-base") return json({ receitaBase: 350000 });
    if (path === "/api/parametros-cmv") return json({ id: 1, version: 1, percentualIdeal: 30 });
    if (path === "/api/relatorios/desperdicio") return json(Array.from({ length: 10 }, (_, i) => ({ dataHora: "2026-10-12T19:26:00",
      produtoId: i + 1, produtoNome: NOME, unidadeMedida: "KG", quantidade: 123.456, valorPrejuizo: 12345.67,
      motivo: "DETERIORACAO", descricaoMotivo: "Deterioração" })));
    if (path === "/api/relatorios/consumo-medio") return json(Array.from({ length: 10 }, (_, i) => ({ produtoId: i + 1,
      produtoNome: NOME, unidadeMedida: "KG", totalConsumidoPeriodo: 12345.678, diasPeriodo: 30, consumoMedioDiario: 411.523 })));
    if (path === "/api/dashboard/resumo") return json({ totalProdutosAtivos: 186, produtosEstoqueBaixo: 53,
      lotesProximosVencimento: 15, lotesVencidos: 4, produtosAbertosAtivos: 7, balancosPendentes: 1,
      cmvPeriodo: cmv.cmv, cmvPercentual: cmv.cmvPercentual, cmvIdeal: 30, valorDesperdicioPeriodo: 7000,
      perdasNaoExplicadasPeriodo: 1456.78, valorAjustesPositivosBalancos: 12345.67, diferencaCmvParaMeta: 5.27 });
    return route.fallback();
  });
}

async function conferirLargura(page: Page) {
  const excesso = await page.evaluate(() => ({ largura: innerWidth, pagina: document.documentElement.scrollWidth - innerWidth,
    tabelas: [...document.querySelectorAll<HTMLElement>(".table-wrap")]
      .filter(e => e.getBoundingClientRect().height > 0).map(e => e.scrollWidth - e.clientWidth) }));
  expect(excesso.pagina, "A página inteira deve caber na tela").toBeLessThanOrEqual(1);
  if (excesso.largura <= 1280) {
    expect(excesso.tabelas.every(v => v <= 1), "Todas as colunas devem estar disponíveis sem rolagem lateral").toBe(true);
  }
}

for (const largura of [320, 390, 720, 768, 1024, 1180, 1440]) {
  test(`telas cabem em ${largura}px sem esconder informações`, async ({ page }) => {
    test.setTimeout(120000);
    await page.setViewportSize({ width: largura, height: 900 });
    await prepararTelas(page);
    for (const rota of ["/", "/estoque", "/catalogo", "/usuarios", "/consumo", "/desperdicio", "/movimentacoes", "/relatorios", "/balanco", "/notas-fiscais?id=1"]) {
      await page.goto(rota);
      await expect(page.locator("h1")).toBeVisible();
      await expect(page.getByText(/^Carregando/)).toHaveCount(0);
      await page.evaluate(() => document.fonts.ready);
      if (rota === "/estoque") {
        await expect(page.getByRole("button", { name: NOME, exact: true })).toBeVisible();
        await conferirLargura(page);
        await page.getByRole("button", { name: NOME, exact: true }).click();
        await expect(page.getByRole("dialog")).toBeVisible();
        await expect(page.getByText("LOTE-COM-IDENTIFICACAO-LONGA-DO-FORNECEDOR", { exact: true })).toBeVisible();
      }
      if (rota === "/balanco") {
        await page.getByRole("button", { name: "Maximizar balanço #1", exact: true }).click();
        await expect(page.getByLabel(`Contagem de ${NOME}`)).toBeVisible();
      }
      if (rota === "/relatorios") {
        const listas = page.locator(".relatorio-lista-rolagem .table-wrap");
        await expect(listas).toHaveCount(2);
        for (const lista of await listas.all()) {
          await expect(lista.locator("tbody tr")).toHaveCount(10);
          const medida = await lista.evaluate(elemento => {
            const linhas = elemento.querySelectorAll("tbody tr");
            return { rolagem: elemento.scrollHeight > elemento.clientHeight,
              excessoOitavo: linhas[7].getBoundingClientRect().bottom - elemento.getBoundingClientRect().bottom };
          });
          expect(medida.rolagem, "Mais de oito itens devem permitir rolagem vertical").toBe(true);
          expect(Math.abs(medida.excessoOitavo), "A altura deve acompanhar oito linhas adaptadas").toBeLessThanOrEqual(3);
        }
      }
      await conferirLargura(page);
    }
    await abrirMenu(page);
    await expect(page.getByRole("button", { name: "Sair da conta" })).toBeVisible();
  });
}

for (const largura of [320, 390, 768, 1024]) {
  test(`login, cadastro e plataforma cabem em ${largura}px`, async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: largura, height: 900 });
    await prepararTelas(page, "PLATAFORMA");
    await page.route("**/api/plataforma/restaurantes", route => route.fulfill({ json: [{ id: 1,
      nome: "Restaurante com nome completo, identificação e unidade operacional", ativo: true,
      dataHoraCriacao: "2026-10-12T19:26:00", totalUsuarios: 3 }] }));
    await page.route("**/api/backups", route => route.fulfill({ json: [{
      nome: "estoq-20261012-192600-12345678-1234-1234-1234-123456789abc.dump", tamanhoBytes: 1234567,
      criadoEm: "2026-10-12T19:26:00Z" }] }));
    for (const rota of ["/plataforma", "/backup"]) {
      await page.goto(rota);
      await expect(page.locator("h1")).toBeVisible();
      await expect(page.getByText(/^Carregando/)).toHaveCount(0);
      const expandir = page.getByRole("button", { name: "Maximizar lista", exact: true });
      if (await expandir.isVisible()) await expandir.click();
      await conferirLargura(page);
    }
    await page.route("**/api/auth/me", route => route.fulfill({ status: 401, json: { message: "Sem sessão" } }));
    for (const rota of ["/login", "/registro"]) {
      await page.goto(rota);
      await expect(page.locator("input[type=email]")).toBeVisible();
      await page.evaluate(() => document.fonts.ready);
      await conferirLargura(page);
    }
  });
}
