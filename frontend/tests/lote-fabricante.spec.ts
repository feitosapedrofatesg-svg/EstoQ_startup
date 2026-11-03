import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

test("entrada manual preserva o número opcional do fabricante", async ({ page }) => {
  await preparar(page);
  const envios: Record<string, unknown>[] = [];
  await page.route("**/api/produtos/1", route => route.fulfill({ json: { id: 1, nome: "Arroz", unidadeMedida: "KG" } }));
  await page.route("**/api/entradas", route => {
    envios.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  await page.goto("/movimentacoes?produto=1");
  await page.getByRole("button", { name: "Registrar entrada", exact: true }).click();
  const modal = page.getByRole("dialog");
  await modal.getByRole("textbox", { name: "Quantidade", exact: true }).fill("2");
  await modal.locator("#ent-valor").fill("20");
  await modal.getByRole("textbox", { name: "Lote do fabricante (opcional)" }).fill(" FAB-123 ");
  await expect(modal.locator("#entrada-lote-fabricante")).toHaveAttribute("maxlength", "50");
  await modal.getByRole("button", { name: "Registrar entrada", exact: true }).click();
  await expect(modal).toHaveCount(0);
  expect(envios[0]).toMatchObject({ produtoId: 1, quantidade: 2, valorTotalPago: 20, loteFabricante: "FAB-123" });
});

test("entrada por embalagem envia duas embalagens de 5kg e mostra 10kg", async ({ page }) => {
  await preparar(page);
  await page.route("**/api/produtos/1", route => route.fulfill({ json: { id: 1, nome: "Arroz", unidadeMedida: "KG" } }));
  let envio: any;
  await page.route("**/api/entradas", route => { envio = route.request().postDataJSON(); return route.fulfill({ status: 204 }); });
  await page.goto("/movimentacoes?produto=1");
  await page.getByRole("button", { name: "Registrar entrada", exact: true }).click();
  const modal = page.getByRole("dialog");
  await modal.getByLabel("Como informar a compra").selectOption("embalagem");
  await modal.getByRole("textbox", { name: "Quantidade de embalagens", exact: true }).fill("2");
  await modal.locator("#ent-valor").fill("60");
  await modal.getByRole("button", { name: "Registrar entrada", exact: true }).click();
  await expect(modal.getByRole("alert")).toContainText("conteúdo");
  await modal.getByRole("textbox", { name: "Conteúdo de cada embalagem", exact: true }).fill("5");
  await expect(modal.getByText("Entrada: 10 KG", { exact: true })).toBeVisible();
  await modal.getByRole("button", { name: "Registrar entrada", exact: true }).click();
  await expect(modal).toHaveCount(0);
  expect(envio).toMatchObject({ quantidade: 2, unidadeCompra: "UN", conteudoEmbalagem: 5, unidadeConteudo: "KG", valorTotalPago: 60 });
});
