import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

async function prepararMovimentacoes(page: Page) {
  await preparar(page);
  await page.clock.install({ time: new Date("2026-10-25T18:00:00Z") });
  const movimentos = [
    { id: 75, tipo: "AJUSTE", produtoNome: "Óleo composto de soja", quantidade: -500, unidadeMedida: "ML",
      diferencaApurada: -500, valorAjuste: 13.19, valorPrejuizo: 13.19, motivo: "OUTRO" },
    { id: 74, tipo: "AJUSTE", produtoNome: "Molho barbecue", quantidade: 1.99, unidadeMedida: "KG",
      diferencaApurada: 1.99, valorAjuste: 19.51 },
    { id: 73, tipo: "DESPERDICIO", produtoNome: "Acelga", quantidade: 1, unidadeMedida: "UN",
      valorPrejuizo: 3.95, motivo: "VENCIMENTO" },
    { id: 104, tipo: "DESPERDICIO", produtoNome: "Batata", quantidade: 1, unidadeMedida: "KG",
      valorPrejuizo: 6.45, motivo: "VENCIMENTO" },
    { id: 105, tipo: "AJUSTE", produtoNome: "Produto sem custo", quantidade: 3, unidadeMedida: "UN",
      diferencaApurada: 3, valorAjuste: 0 },
  ].map(m => ({ dataHora: "2026-10-25T15:00:00", produtoId: m.id, loteId: m.id,
    loteCodigo: `LOTE-${m.id}`, usuarioId: 1, usuarioNome: "Responsável A", valorPrejuizo: null, ...m }));

  await page.route("**/api/movimentacoes**", route => {
    const tipo = new URL(route.request().url()).searchParams.get("tipo");
    const dados = movimentos.filter(m => !tipo || m.tipo === tipo
      || (tipo === "DESPERDICIO" && m.valorPrejuizo !== null));
    return route.fulfill({ json: dados });
  });
  await page.route("**/api/relatorios/cmv?*", route => route.fulfill({ json: {
    valorEstoqueInicial: 0, valorCompras: 6116.11, valorEstoqueFinal: 6036.59,
    cmv: 79.52, receitaBase: 404, cmvPercentual: 19.68, percentualIdeal: 30,
    diferencaPercentualParaMeta: -10.32, valorConsumoRegistrado: 81.43,
    valorDesperdicio: 23.59, percentualDesperdicioSobreCmv: 29.67,
    valorPerdasNaoExplicadas: -25.50, faturamentoInformado: 404,
    diasFaturamentoPendentes: 0, receitaPorPeriodo: false,
  } }));
}

for (const width of [320, 1440]) {
  test(`ajustes mostram reais e unidades e perdas conferem entre telas em ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await prepararMovimentacoes(page);
    await page.goto("/movimentacoes");
    const oleo = page.getByRole("row").filter({ hasText: "Óleo composto de soja" });
    await expect(oleo.locator('td[data-label="Quantidade"]')).toHaveText("−500 ml");
    await expect(oleo.locator('td[data-label="Valor"]')).toHaveText(/R\$\s*13,19/);
    const sobra = page.getByRole("row").filter({ hasText: "Molho barbecue" });
    await expect(sobra.locator('td[data-label="Quantidade"]')).toHaveText("+1,99 kg");
    await expect(sobra.locator('td[data-label="Valor"]')).toHaveText(/R\$\s*19,51/);
    const semCusto = page.getByRole("row").filter({ hasText: "Produto sem custo" });
    await expect(semCusto.locator('td[data-label="Quantidade"]')).toHaveText("+3 un");
    await expect(semCusto.locator('td[data-label="Valor"]')).toHaveText(/R\$\s*0,00/);
    await expect(page.locator(".mini-stat").nth(2)).toContainText(/R\$\s*23,59/);
    await page.getByRole("button", { name: "Desperdícios", exact: true }).click();
    await expect(page.locator("tbody tr")).toHaveCount(3);
    await expect(page.locator(".mini-stat").nth(2)).toContainText(/R\$\s*23,59/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);

    await page.goto("/desperdicio");
    await expect(page.locator("p").filter({ hasText: /3 registro\(s\).*Prejuízo/ })).toContainText(/R\$\s*23,59/);
    await expect(oleo.locator('td[data-label="Quantidade"]')).toHaveText("500 ml");
    await expect(page.getByRole("row").filter({ hasText: "Acelga" }).locator('td[data-label="Quantidade"]')).toHaveText("1 un");
    await expect(page.getByRole("row").filter({ hasText: "Batata" }).locator('td[data-label="Quantidade"]')).toHaveText("1 kg");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);

    await page.goto("/relatorios?inicio=2026-09-26&fim=2026-10-25");
    const desperdicio = page.getByRole("row").filter({ has: page.getByText("Desperdício", { exact: true }) });
    await expect(desperdicio).toContainText(/R\$\s*23,59/);
    await expect(page.getByRole("row").filter({ hasText: "Diferença para a meta" })).toContainText("-10,3%");
  });
}

test("ajuste sem valor monetário informado não mostra quantidade como reais", async ({ page }) => {
  await preparar(page);
  await page.route("**/api/movimentacoes**", route => route.fulfill({ json: [{
    id: 1, tipo: "AJUSTE", dataHora: "2026-10-25T15:00:00", produtoId: 1,
    produtoNome: "Produto sem custo informado", quantidade: 500, unidadeMedida: "ML",
    diferencaApurada: 500, valorAjuste: null, valorPrejuizo: null,
  }] }));
  await page.goto("/movimentacoes");
  await expect(page.locator('td[data-label="Quantidade"]')).toHaveText("+500 ml");
  await expect(page.locator('td[data-label="Valor"]')).toHaveText("—");
});
