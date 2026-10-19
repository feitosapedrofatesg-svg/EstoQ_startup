import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

async function prepararParametros(page: Page, niveis = [0, 0, 0]) {
  await preparar(page);
  const envios: Record<string, unknown>[] = [];
  await page.route("**/api/produtos", route => {
    if (route.request().method() !== "POST") return route.fallback();
    return route.fulfill({ json: { ...route.request().postDataJSON(), id: 999, version: 0, ativo: true } });
  });
  await page.route("**/api/produtos/1", route => {
    if (route.request().method() !== "PUT") return route.fallback();
    return route.fulfill({ json: { ...route.request().postDataJSON(), id: 1, version: 1, ativo: true } });
  });
  await page.route("**/api/parametros-estoque/**", route => {
    if (route.request().method() === "PUT") {
      const envio = route.request().postDataJSON(); envios.push(envio);
      return route.fulfill({ json: { ...envio, id: 10, version: envio.version + 1 } });
    }
    const novo = new URL(route.request().url()).pathname.endsWith("/999");
    return route.fulfill({ json: { id: 10, version: novo ? 0 : 4, produtoId: novo ? 999 : 1,
      estoqueMinimo: novo ? 3 : niveis[0], estoqueMedio: novo ? 3 : niveis[1], estoqueMaximo: novo ? 3 : niveis[2],
      consumoMedioDiario: 0, tempoReposicaoDias: 0, periodoAnaliseDias: 30, diasAlertaVencimento: 7 } });
  });
  await page.goto("/catalogo");
  return envios;
}

async function novoProduto(page: Page) {
  await page.getByRole("button", { name: "Novo produto", exact: true }).click();
  const modal = page.getByRole("dialog");
  await modal.getByRole("textbox", { name: "Nome", exact: true }).fill("Arroz novo");
  await modal.getByRole("button", { name: "Escolha a categoria…", exact: true }).click();
  await modal.getByRole("option", { name: "Categoria 001", exact: true }).click();
  return modal;
}

test("novo produto preenche os níveis em três e salva parâmetros válidos", async ({ page }) => {
  const envios = await prepararParametros(page);
  const modal = await novoProduto(page);
  for (const label of ["Estoque mínimo", "Estoque médio", "Estoque máximo"]) {
    await expect(modal.getByLabel(label, { exact: true })).toHaveValue("3");
  }
  await modal.getByRole("button", { name: "Salvar produto", exact: true }).click();
  await expect(modal).not.toBeVisible();
  expect(envios).toEqual([expect.objectContaining({ produtoId: 999, estoqueMinimo: 3, estoqueMedio: 3,
    estoqueMaximo: 3, consumoMedioDiario: 0, version: 0 })]);
});

for (const niveis of [[7, 8, 9], [0, 0, 0]]) {
  test(`editar nome preserva níveis existentes ${niveis.join("/")}`, async ({ page }) => {
    const envios = await prepararParametros(page, niveis);
    await page.getByRole("button", { name: "Produto 001", exact: true }).click();
    const modal = page.getByRole("dialog");
    await expect(modal.getByLabel("Estoque mínimo", { exact: true })).toHaveValue(String(niveis[0]));
    await modal.getByRole("textbox", { name: "Nome", exact: true }).fill("Nome corrigido");
    await modal.getByRole("button", { name: "Salvar produto", exact: true }).click();
    await expect(modal).not.toBeVisible();
    expect(envios[0]).toMatchObject({ estoqueMinimo: niveis[0], estoqueMedio: niveis[1], estoqueMaximo: niveis[2], version: 4 });
  });
}

test("é possível remover os níveis padrão e optar por zero", async ({ page }) => {
  const envios = await prepararParametros(page);
  const modal = await novoProduto(page);
  for (const label of ["Estoque mínimo", "Estoque médio", "Estoque máximo"]) {
    await modal.getByLabel(label, { exact: true }).fill("");
  }
  await modal.getByRole("button", { name: "Salvar produto", exact: true }).click();
  await expect(modal).not.toBeVisible();
  expect(envios[0]).toMatchObject({ estoqueMinimo: 0, estoqueMedio: 0, estoqueMaximo: 0, consumoMedioDiario: 0 });
});
