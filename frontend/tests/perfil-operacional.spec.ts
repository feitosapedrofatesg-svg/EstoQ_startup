import { test, expect } from "@playwright/test";
import { preparar, abrirMenu } from "./fixtures";

for (const width of [320, 1440]) {
  test(`perfil COZINHA aparece como Operacional sem mudar os acessos em ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await preparar(page, "COZINHA");
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/estoque$/);
    await abrirMenu(page);
    await expect(page.locator(".role-tag")).toHaveText("Operacional");
    for (const name of ["Estoque", "Consumo", "Desperdício", "Balanço físico", "Catálogo"]) {
      await expect(page.getByRole("link", { name, exact: true })).toBeVisible();
    }
    for (const name of ["Movimentações", "Usuários", "Relatórios e CMV"]) {
      await expect(page.getByRole("link", { name, exact: true })).toHaveCount(0);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.goto("/movimentacoes");
    await expect(page.getByText("Esta área não está disponível para o seu perfil", { exact: true })).toBeVisible();
    await expect(page.locator(".empty__text")).toContainText("o Operacional registra");
  });
}

test("usuário existente exibe Operacional e mantém COZINHA ao salvar edição", async ({ page }) => {
  await preparar(page);
  const envios: Record<string, unknown>[] = [];
  await page.route("**/api/usuarios/1", route => {
    if (route.request().method() !== "PUT") return route.fallback();
    envios.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  await page.goto("/usuarios");
  const linha = page.getByRole("row").filter({ hasText: "pessoa1@teste.com" });
  await expect(linha.getByText("Operacional", { exact: true })).toBeVisible();
  await expect(linha.getByText("Cozinha", { exact: true })).toHaveCount(0);
  await linha.getByRole("button", { name: "Editar", exact: true }).click();
  const modal = page.getByRole("dialog");
  await expect(modal.getByRole("combobox")).toHaveValue("COZINHA");
  await expect(modal.getByRole("option", { selected: true })).toHaveText("Operacional — estoque e operação diária");
  await modal.getByPlaceholder("Nome e sobrenome").fill("Operador atualizado");
  await modal.getByRole("button", { name: "Salvar", exact: true }).click();
  await expect(modal).toHaveCount(0);
  expect(envios).toHaveLength(1);
  expect(envios[0]).toMatchObject({ nome: "Operador atualizado", perfil: "COZINHA", version: 0 });
});
