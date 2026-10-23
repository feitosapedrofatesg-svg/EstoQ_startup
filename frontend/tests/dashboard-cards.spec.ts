import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

const DESTINOS = [
  { label: "Produtos ativos", to: "/catalogo", title: "Catálogo" },
  { label: "Estoque baixo", to: "/estoque", title: "Estoque" },
  { label: "Próximos a vencer", to: "/estoque", title: "Estoque" },
  { label: "Vencidos", to: "/desperdicio", title: "Desperdício" },
  { label: "Embalagens abertas", to: "/consumo", title: "Consumo" },
  { label: "Balanços pendentes", to: "/balanco", title: "Balanço físico" },
];

async function prepararDashboard(page: Page, perfil = "ADMIN", zero = false) {
  const estado = await preparar(page, perfil);
  await page.route("**/api/dashboard/resumo**", route => route.fulfill({ json: {
    totalProdutosAtivos: zero ? 0 : 125, produtosEstoqueBaixo: zero ? 0 : 3,
    lotesProximosVencimento: zero ? 0 : 2, lotesVencidos: zero ? 0 : 1,
    produtosAbertosAtivos: zero ? 0 : 4, balancosPendentes: zero ? 0 : 1,
    cmvPeriodo: 100, cmvPercentual: 10, cmvIdeal: 30, receitaBase: 1000,
    diasFaturamentoPendentes: 0, receitaPorPeriodo: false, valorDesperdicioPeriodo: 0,
    perdasNaoExplicadasPeriodo: 0, diferencaCmvParaMeta: -20,
  } }));
  await page.goto("/dashboard");
  return estado;
}

for (const width of [320, 1440]) {
  test(`ADMIN abre os seis cards do Dashboard em ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await prepararDashboard(page);
    const cards = page.locator(".stat-grid");
    await expect(cards.getByRole("link")).toHaveCount(6);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    for (const destino of DESTINOS) {
      const link = cards.getByRole("link", { name: new RegExp(`^${destino.label}`) });
      await expect(link).toHaveAttribute("href", destino.to);
      const bounds = await link.boundingBox();
      expect(bounds!.width).toBeGreaterThanOrEqual(44);
      expect(bounds!.height).toBeGreaterThanOrEqual(44);
      await link.click();
      await expect(page).toHaveURL(new RegExp(`${destino.to}$`));
      await expect(page.getByRole("heading", { level: 1, name: destino.title, exact: true })).toBeVisible();
      await page.goBack();
      await expect(cards.getByRole("link")).toHaveCount(6);
    }
  });
}

test("cards aceitam Tab e Enter, preservando cores e indicadores zerados", async ({ page }) => {
  await prepararDashboard(page, "ADMIN", true);
  const links = page.locator(".stat-grid").getByRole("link");
  await expect(links).toHaveCount(6);
  await expect(links.first().locator(".stat__value")).toHaveText("0");
  const original = await links.first().locator(".stat__value").evaluate(e => getComputedStyle(e).color);
  expect(original).toBe(await page.locator("body").evaluate(e => getComputedStyle(e).color));
  await links.first().hover();
  expect(await links.first().evaluate(e => getComputedStyle(e).textDecorationLine)).toBe("none");
  await links.first().focus();
  await page.keyboard.press("Tab");
  await expect(links.nth(1)).toBeFocused();
  expect(await links.nth(1).evaluate(e => getComputedStyle(e).boxShadow)).not.toBe("none");
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/estoque$/);
});

test("NUTRICIONISTA vê os valores sem links para áreas operacionais restritas", async ({ page }) => {
  const estado = await prepararDashboard(page, "NUTRICIONISTA");
  const cards = page.locator(".stat-grid");
  await expect(cards.locator(".stat")).toHaveCount(6);
  await expect(cards.getByRole("link")).toHaveCount(0);
  await expect(cards.locator("[tabindex]")).toHaveCount(0);
  for (const destino of DESTINOS) await expect(cards.getByText(destino.label, { exact: true })).toBeVisible();
  expect(estado.consultas.some(path => /^\/api\/(estoque|produtos|balancos$|produtos-abertos)/.test(path))).toBe(false);
  await page.getByRole("link", { name: "Relatórios", exact: true }).click();
  await expect(page).toHaveURL(/\/relatorios$/);
});
