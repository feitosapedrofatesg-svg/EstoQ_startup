import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

for (const largura of [320, 1440]) {
  test(`home da raiz mostra funcionalidades e leva ao login em ${largura}px`, async ({ page }) => {
    await preparar(page);
    await page.route("**/api/auth/me", route => route.fulfill({ status: 401, json: {} }));
    await page.setViewportSize({ width: largura, height: 900 });
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Menos desperdício");
    await expect(page.getByRole("img", { name: /Painel do EstoQ/ })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Relatórios e CMV" })).toBeVisible();
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
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `/tmp/estoq-sobre-${largura}.png`, fullPage: true });
    await page.getByRole("link", { name: "Política de Privacidade", exact: true }).click();
    await expect(page).toHaveURL(/\/privacidade$/);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Política de Privacidade");
    await expect(page.getByRole("heading", { name: "Quando você entra com Google" })).toBeVisible();
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
