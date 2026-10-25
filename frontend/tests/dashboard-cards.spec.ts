import { test, expect, type Locator, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

const DESTINOS = [
  { label: "Produtos ativos", to: "/catalogo", title: "Catálogo" },
  { label: "Estoque baixo", to: "/estoque", title: "Estoque" },
  { label: "Próximos a vencer", to: "/estoque", title: "Estoque" },
  { label: "Vencidos", to: "/desperdicio", title: "Desperdício" },
  { label: "Embalagens abertas", to: "/desperdicio", title: "Desperdício" },
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
    perdasNaoExplicadasPeriodo: 0, valorAjustesPositivosBalancos: 0, diferencaCmvParaMeta: -20,
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

async function deslocamento(control: Locator) {
  return control.evaluate(el => {
    const matrix = new DOMMatrix(getComputedStyle(el).transform);
    return [Math.round(matrix.m41 * 100) / 100, Math.round(matrix.m42 * 100) / 100];
  });
}

for (const width of [320, 1440]) {
  test(`efeito tátil pressiona e retorna sem alterar a navegação em ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await prepararDashboard(page);
    const card = page.locator(".stat-grid").getByRole("link", { name: /^Embalagens abertas/ });
    await expect(card).toHaveClass(/dashboard-tactile-control/);
    await card.scrollIntoViewIfNeeded();
    await page.screenshot({ path: test.info().outputPath(`dashboard-tactile-${width}.png`) });
    await card.hover();
    await page.mouse.down();
    await expect.poll(() => deslocamento(card)).toEqual([-1, 6]);
    await page.screenshot({ path: test.info().outputPath(`dashboard-tactile-${width}-pressionado.png`) });
    await page.mouse.move(1, 1);
    await page.mouse.up();
    await expect.poll(() => deslocamento(card)).toEqual([0, 0]);
    await expect(page).toHaveURL(/\/dashboard$/);
    // O canto superior também continua acionável durante a animação.
    await card.click({ position: { x: 12, y: 3 }, delay: 100 });
    await expect(page).toHaveURL(/\/desperdicio$/);
    await expect(page.getByRole("heading", { name: "Embalagens abertas", exact: true })).toBeVisible();
    await expect(page.locator(".dashboard-tactile-control")).toHaveCount(0);
  });
}

test("botões do Dashboard respondem ao teclado e preservam o foco", async ({ page }) => {
  await prepararDashboard(page);
  const reload = page.getByRole("button", { name: "Recarregar faturamento", exact: true });
  await reload.focus();
  await page.keyboard.down(" ");
  await expect.poll(() => deslocamento(reload)).toEqual([-1, 6]);
  await page.keyboard.up(" ");
  await expect.poll(() => deslocamento(reload)).toEqual([0, 0]);
  await expect(reload).toBeFocused();
  expect(await reload.evaluate(el => getComputedStyle(el).outlineStyle)).toBe("solid");
  await expect(page.getByRole("button", { name: "Salvar faturamento", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Salvar faturamento", exact: true })).not.toHaveClass(/dashboard-tactile-control/);
  await expect(page.locator(".bell__btn")).not.toHaveClass(/dashboard-tactile-control/);
});

test("cancelamento de toque e perda de foco soltam o controle", async ({ page }) => {
  await prepararDashboard(page);
  const card = page.locator(".stat-grid").getByRole("link").first();
  await card.dispatchEvent("pointerdown", { button: 0, isPrimary: true, pointerId: 7, pointerType: "touch" });
  await expect.poll(() => deslocamento(card)).toEqual([-1, 6]);
  await page.evaluate(() => window.dispatchEvent(new PointerEvent("pointercancel", { pointerId: 7 })));
  await expect.poll(() => deslocamento(card)).toEqual([0, 0]);
  await card.dispatchEvent("pointerdown", { button: 0, isPrimary: true, pointerId: 8, pointerType: "touch" });
  await expect.poll(() => deslocamento(card)).toEqual([-1, 6]);
  await page.evaluate(() => window.dispatchEvent(new Event("blur")));
  await expect.poll(() => deslocamento(card)).toEqual([0, 0]);
});

test("movimento reduzido não desloca controles e outras telas não recebem o efeito", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await prepararDashboard(page);
  const card = page.locator(".stat-grid").getByRole("link").first();
  await card.hover();
  await page.mouse.down();
  expect(await deslocamento(card)).toEqual([0, 0]);
  expect(await card.evaluate(el => parseFloat(getComputedStyle(el).transitionDuration))).toBeLessThanOrEqual(0.001);
  await page.mouse.move(1, 1);
  await page.mouse.up();
  await page.goto("/usuarios");
  await expect(page.getByRole("button", { name: "Novo usuário", exact: true })).toBeVisible();
  await expect(page.locator(".dashboard-tactile-control")).toHaveCount(0);
});

test("alterar movimento reduzido com a página aberta interrompe e reativa a pressão", async ({ page }) => {
  await prepararDashboard(page);
  const card = page.locator(".stat-grid").getByRole("link").first();
  await card.dispatchEvent("pointerdown", { button: 0, isPrimary: true, pointerId: 9 });
  await expect.poll(() => deslocamento(card)).toEqual([-1, 6]);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(page.locator(".dashboard-tactile")).toHaveAttribute("data-reduced-motion", "true");
  await expect.poll(() => deslocamento(card)).toEqual([0, 0]);
  await card.dispatchEvent("pointerdown", { button: 0, isPrimary: true, pointerId: 10 });
  expect(await deslocamento(card)).toEqual([0, 0]);
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await expect(page.locator(".dashboard-tactile")).toHaveAttribute("data-reduced-motion", "false");
  await card.dispatchEvent("pointerdown", { button: 0, isPrimary: true, pointerId: 11 });
  await expect.poll(() => deslocamento(card)).toEqual([-1, 6]);
  await card.dispatchEvent("pointercancel", { pointerId: 11 });
  await expect.poll(() => deslocamento(card)).toEqual([0, 0]);
});
