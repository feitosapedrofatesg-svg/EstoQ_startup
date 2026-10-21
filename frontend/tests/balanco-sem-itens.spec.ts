import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

async function prepararVazio(page: Page, perfil = "ADMIN", falhar = false) {
  await preparar(page, perfil);
  const estado = {
    confirmacoes: [] as unknown[],
    balanco: { id: 1, version: 1, tipo: "PARCIAL", status: "EM_ANDAMENTO",
      dataHora: "2026-10-21T10:00:00", usuarioId: 1, usuarioNome: "Responsável A",
      categoriaIds: [1], categoriaNomes: ["Categoria sem produtos"], itens: [] },
  };
  await page.route("**/api/balancos**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/balancos/proximo") return route.fulfill({ json: [] });
    if (path === "/api/balancos/1/confirmar" && route.request().method() === "POST") {
      estado.confirmacoes.push(route.request().postDataJSON());
      if (falhar) return route.fulfill({ status: 503, json: { message: "Falha de teste" } });
      estado.balanco.status = "CONCLUIDO";
      estado.balanco.version++;
      return route.fulfill({ json: estado.balanco });
    }
    if (path === "/api/balancos") return route.fulfill({ json: [estado.balanco] });
    return route.fulfill({ json: [] });
  });
  await page.goto("/balanco");
  return estado;
}

for (const width of [320, 1440]) {
  test(`ADMIN finaliza balanço com 0 itens mediante confirmação em ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const estado = await prepararVazio(page);
    await page.getByRole("button", { name: "Finalizar sem itens", exact: true }).click();
    const modal = page.getByRole("dialog");
    await expect(modal).toContainText("0 itens");
    await modal.getByRole("button", { name: "Cancelar", exact: true }).click();
    expect(estado.confirmacoes).toEqual([]);
    await page.getByRole("button", { name: "Finalizar sem itens", exact: true }).click();
    await modal.getByRole("button", { name: "Finalizar sem itens", exact: true }).click();
    await expect(page.getByText("Balanço confirmado", { exact: true })).toBeVisible();
    expect(estado.confirmacoes).toEqual([{ version: 1, itens: [] }]);
    await expect(page.locator(".balanco__title")).toContainText("Concluído");
    await expect(page.getByRole("button", { name: "Gerar ajustes de estoque" })).toHaveCount(0);
    await page.reload();
    await expect(page.locator(".balanco__title")).toContainText("Concluído");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}

test("falha na API mantém balanço vazio em contagem", async ({ page }) => {
  await prepararVazio(page, "ADMIN", true);
  await page.getByRole("button", { name: "Finalizar sem itens", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Finalizar sem itens", exact: true }).click();
  await expect(page.getByText("Operação não concluída", { exact: true })).toBeVisible();
  await expect(page.locator(".balanco__title")).toContainText("Em contagem");
  await expect(page.getByText("Balanço confirmado", { exact: true })).toHaveCount(0);
});

test("COZINHA não pode finalizar balanço sem itens", async ({ page }) => {
  await prepararVazio(page, "COZINHA");
  await expect(page.locator(".balanco__title")).toContainText("Em contagem");
  await expect(page.getByRole("button", { name: "Finalizar sem itens", exact: true })).toHaveCount(0);
});
