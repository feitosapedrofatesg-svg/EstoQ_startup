import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

test("balanço zerado aceita o saldo do sistema e libera a confirmação", async ({ page }) => {
  await preparar(page, "ADMIN");

  const estado = {
    preenchimentos: [] as unknown[],
    balanco: {
      id: 1,
      version: 1,
      ativo: true,
      dataHora: "2026-10-21T10:00:00",
      tipo: "GERAL",
      status: "EM_ANDAMENTO",
      usuarioId: 1,
      usuarioNome: "Responsável A",
      categoriaIds: [],
      categoriaNomes: [],
      itens: [{
        id: 11,
        version: 1,
        produtoId: 1,
        produtoNome: "Produto zerado",
        unidadeMedida: "KG",
        categoriaId: 1,
        categoriaNome: "Grãos",
        quantidadeSistema: 0,
        quantidadeFisica: null as number | null,
        diferenca: null as number | null,
        ajusteAplicado: false,
      }],
    },
  };

  await page.route("**/api/balancos**", async route => {
    const caminho = new URL(route.request().url()).pathname;
    if (caminho === "/api/balancos/proximo") return route.fulfill({ json: [] });
    if (caminho === "/api/balancos" && route.request().method() === "GET") {
      return route.fulfill({ json: [estado.balanco] });
    }
    if (caminho === "/api/balancos/1/contagens/sistema" && route.request().method() === "POST") {
      estado.preenchimentos.push(route.request().postDataJSON());
      for (const item of estado.balanco.itens) {
        if (item.quantidadeFisica === null) {
          item.quantidadeFisica = item.quantidadeSistema;
          item.diferenca = 0;
          item.version++;
        }
      }
      estado.balanco.version++;
      return route.fulfill({ json: estado.balanco });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/balanco");
  await page.getByRole("button", { name: "Maximizar balanço #1", exact: true }).click();
  await expect(page.getByLabel("Contagem de Produto zerado")).toHaveValue("0");

  await page.getByRole("button", { name: "Aceitar saldo do sistema", exact: true }).click();
  const modal = page.getByRole("dialog");
  await expect(modal).toContainText("1 item");
  await expect(modal).toContainText("registra 0 nos itens pendentes");
  await modal.getByRole("button", { name: "Aceitar saldo", exact: true }).click();

  await expect(page.getByText("Saldo do sistema aceito", { exact: true })).toBeVisible();
  expect(estado.preenchimentos).toEqual([{ version: 1 }]);
  await expect(page.getByRole("button", { name: "Confirmar balanço", exact: true })).toBeVisible();
});
