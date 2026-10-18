import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

test("impressão do balanço exibe nomes como texto e não executa HTML", async ({ page, context }) => {
  await context.addInitScript(() => { window.print = () => {}; });
  await preparar(page);
  const nome = '<img src=x onerror="window.opener.injetado=true">';
  await page.route("**/api/balancos", route => route.fulfill({ json: [{ id: 1, version: 0,
    tipo: "PARCIAL", status: "CONCLUIDO", categoriaNomes: [nome], usuarioNome: nome,
    dataHora: "2026-10-17T12:00:00", itens: [{ id: 1, version: 0, produtoId: 1,
      produtoNome: nome, unidadeMedida: "KG", quantidadeSistema: 2, quantidadeFisica: 2,
      diferenca: 0, ajusteAplicado: true }] }] }));
  await page.goto("/balanco");
  const aberta = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Imprimir balanço 1", exact: true }).click();
  const impressao = await aberta;
  await expect(impressao.locator("tbody td").first()).toHaveText(nome);
  await expect(impressao.locator("body")).toContainText(`Categorias: ${nome}`);
  await expect(impressao.locator("img,script")).toHaveCount(0);
  expect(await page.evaluate(() => "injetado" in window)).toBe(false);
});

test("teclado permanece no modal e volta ao botão que o abriu", async ({ page }) => {
  await preparar(page);
  await page.goto("/catalogo");
  const abrir = page.getByRole("button", { name: "Novo produto", exact: true });
  await abrir.click();
  const modal = page.getByRole("dialog");
  await expect(modal).toBeVisible();
  const fechar = modal.getByRole("button", { name: "Fechar janela", exact: true });
  await fechar.focus();
  await page.keyboard.press("Shift+Tab");
  expect(await modal.evaluate(elemento => elemento.contains(document.activeElement))).toBe(true);
  await page.keyboard.press("Tab");
  await expect(fechar).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(modal).toHaveCount(0);
  await expect(abrir).toBeFocused();
});

test("rótulos mensais usam ano e mês da resposta ISO", async ({ page }) => {
  await preparar(page);
  await page.route("**/api/relatorios/cmv/mensal?**", route => route.fulfill({ json: [
    { periodo: "2026-10", cmv: 50, valorCompras: 100, valorDesperdicio: 5 },
  ] }));
  await page.goto("/relatorios");
  await expect(page.getByRole("cell", { name: "10/2026", exact: true })).toBeVisible();
});
