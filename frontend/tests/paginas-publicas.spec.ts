import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

for (const largura of [320, 1440]) {
  test(`home da raiz mostra funcionalidades e leva ao login em ${largura}px`, async ({ page }) => {
    await preparar(page);
    await page.route("**/api/auth/me", route => route.fulfill({ status: 401, json: {} }));
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width: largura, height: 900 });
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Menos desperdício");
    await expect(page.getByRole("img", { name: /Painel do EstoQ/ })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Relatórios e CMV" })).toBeVisible();
    await expect(page.getByText("reduz equivale a aproximadamente 1% do faturamento do mês.", { exact: false })).toBeVisible();
    await expect(page.locator('a[href^="mailto:"]')).toHaveCount(0);
    await expect(page.getByRole("contentinfo").getByRole("link")).toHaveCount(2);
    // Pontos abaixo da imagem; setas continuam centralizadas só sobre a foto.
    const pista = (await page.locator(".carrossel__pistas").boundingBox())!;
    const pontos = page.locator(".carrossel__pontos");
    const caixaPontos = (await pontos.boundingBox())!;
    expect(caixaPontos.y).toBeGreaterThanOrEqual(pista.y + pista.height - 1);
    expect(Math.abs(caixaPontos.x + caixaPontos.width / 2 - (pista.x + pista.width / 2))).toBeLessThan(1);
    const seta = (await page.getByRole("button", { name: "Próxima tela" }).boundingBox())!;
    expect(Math.abs(seta.y + seta.height / 2 - (pista.y + pista.height / 2))).toBeLessThan(1);
    await expect(pontos.getByRole("button")).toHaveCount(6);
    await page.getByRole("button", { name: "Tela 6 de 6: Central de alertas" }).click();
    await expect(page.locator(".carrossel__pista[data-ativo] img")).toHaveAttribute("src", "/print/alertas.jpg");
    await page.getByRole("button", { name: "Próxima tela" }).click();
    await expect(page.locator(".carrossel__pista[data-ativo] img")).toHaveAttribute("src", "/print/catalogo.jpg");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `/tmp/estoq-home-${largura}.png`, fullPage: true, animations: "disabled" });
    // linha do razão abre e fecha (âncora: o carrossel também nomeia um ponto "Balanço físico")
    const linha = page.getByRole("button", { name: /^Balanço físico/ });
    await expect(linha).toHaveAttribute("aria-expanded", "false");
    await linha.click();
    await expect(linha).toHaveAttribute("aria-expanded", "true");
    await expect(page.getByText("Contagem por produto comparada")).toBeVisible();
    await linha.click();
    await expect(linha).toHaveAttribute("aria-expanded", "false");
    // calculadora de CMV: faturamento × 1%
    await page.getByLabel("Faturamento mensal").fill("200000");
    await expect(page.getByText("R$ 2.000")).toBeVisible();
    await page.getByRole("main").getByRole("link", { name: "Entrar", exact: true }).first().click();
    await expect(page).toHaveURL(/\/login$/);
  });
}

for (const largura of [320, 1440]) {
  test(`apresentação e privacidade abrem sem login e cabem em ${largura}px`, async ({ page }) => {
    await preparar(page);
    await page.route("**/api/auth/me", route => route.fulfill({ status: 401, json: {} }));
    await page.setViewportSize({ width: largura, height: 900 });
    await page.goto("/sobre");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Estoque e CMV");
    await expect(page.getByRole("link", { name: "Cadastrar minha cozinha" })).toHaveAttribute("href", "/registro");
    await expect(page.getByRole("link", { name: "Política de Privacidade", exact: true })).toHaveAttribute("href", "/privacidade");
    await expect(page.getByRole("contentinfo").locator('a[href^="mailto:"]')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `/tmp/estoq-sobre-${largura}.png`, fullPage: true });
    await page.getByRole("link", { name: "Política de Privacidade", exact: true }).click();
    await expect(page).toHaveURL(/\/privacidade$/);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Política de Privacidade");
    await expect(page.getByRole("heading", { name: "Quando você entra com Google" })).toBeVisible();
    await expect(page.getByRole("contentinfo").locator('a[href^="mailto:"]')).toHaveCount(0);
    await expect(page.getByRole("link", { name: "estoq.sistema@gmail.com" }).first()).toHaveAttribute("href", "mailto:estoq.sistema@gmail.com");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `/tmp/estoq-privacidade-${largura}.png`, fullPage: true });
  });
}

test("página de privacidade também fica disponível durante uma sessão", async ({ page }) => {
  await preparar(page);
  await page.goto("/dashboard");
  await page.getByRole("link", { name: "Privacidade", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Política de Privacidade");
  await page.getByRole("link", { name: "Entrar", exact: true }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
});
