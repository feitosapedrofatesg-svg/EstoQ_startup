import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

test("origem da média fica visível e a reposição não segue o filtro histórico", async ({ page }) => {
  const estado = await preparar(page);
  await page.route("**/api/relatorios/reposicao-sugerida*", route => route.fulfill({ json: [
    { produtoId: 1, produtoNome: "Arroz", categoriaNome: "Grãos", unidadeMedida: "KG", saldoAtual: 1,
      estoqueMinimo: 3, consumoMedioDiario: 2, diasReposicao: 7, quantidadeSugerida: 13,
      origemMedia: "AUTOMATICO", periodoAnaliseDias: 30 },
    { produtoId: 2, produtoNome: "Feijão", categoriaNome: "Grãos", unidadeMedida: "KG", saldoAtual: 0,
      estoqueMinimo: 3, consumoMedioDiario: 1, diasReposicao: 7, quantidadeSugerida: 7,
      origemMedia: "MANUAL", periodoAnaliseDias: 30 },
  ] }));
  await page.goto("/relatorios");
  await page.getByRole("button", { name: /Reposição sugerida/ }).click();
  await expect(page.getByText("Automático · 30 dias", { exact: true })).toBeVisible();
  await expect(page.getByText("Manual · sem histórico", { exact: true })).toBeVisible();
  await page.getByLabel("De", { exact: true }).fill("2026-08-01");
  expect(estado.consultas.filter(x => x.includes("reposicao-sugerida")).every(x => !x.includes("inicio="))).toBe(true);
});
