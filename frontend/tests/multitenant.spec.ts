import { test, expect } from "@playwright/test";
import { preparar, abrirMenu, entrarNaLojaB } from "./fixtures";

test("trocar de restaurante limpa os dados anteriormente carregados", async ({ page }) => {
  await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await entrarNaLojaB(page);
  await expect(page.getByText("Ingrediente do restaurante B", { exact: true })).toBeVisible();
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toHaveCount(0);
});

for (const status of [200, 401]) test(`resposta atrasada ${status} da sessão anterior não contamina a nova sessão`, async ({ page }) => {
  const estado = await preparar(page);
  estado.atrasarEstoqueA = true;
  estado.respostaAntiga401 = status === 401;
  await page.goto("/estoque");
  await expect.poll(() => estado.pendentes.length).toBeGreaterThan(0);
  await entrarNaLojaB(page);
  await expect(page.getByText("Ingrediente do restaurante B", { exact: true })).toBeVisible();
  for (const liberar of estado.pendentes) await liberar();
  await abrirMenu(page);
  await page.getByRole("link", { name: "Catálogo", exact: true }).click();
  await abrirMenu(page);
  await page.getByRole("link", { name: "Estoque", exact: true }).click();
  await expect(page.getByText("Ingrediente do restaurante B", { exact: true })).toBeVisible();
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toHaveCount(0);
});

test("cozinha não vê nem acessa a tela de movimentações", async ({ page }) => {
  await preparar(page, "COZINHA");
  await page.goto("/estoque");
  await abrirMenu(page);
  await expect(page.getByRole("link", { name: "Movimentações", exact: true })).toHaveCount(0);
  await page.goto("/movimentacoes");
  await expect(page.getByText("Esta área não está disponível para o seu perfil", { exact: true })).toBeVisible();
});

for (const perfil of ["ADMIN", "COZINHA", "NUTRICIONISTA"]) {
  test(`${perfil} não encontra a tela de backup`, async ({ page }) => {
    await preparar(page, perfil);
    await page.goto("/backup");
    await expect(page.getByText("Esta área não está disponível para o seu perfil", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: /Backup/ })).toHaveCount(0);
  });
}

test("PLATAFORMA encontra o backup completo", async ({ page }) => {
  await preparar(page, "PLATAFORMA");
  await page.goto("/backup");
  await expect(page.getByRole("heading", { name: "Backup completo da plataforma" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Backup completo" })).toBeVisible();
});

test("catálogo, categorias e usuários alcançam registros além dos primeiros cem", async ({ page }) => {
  await preparar(page);
  await page.goto("/catalogo");
  await page.getByRole("button", { name: "Próxima", exact: true }).click();
  await expect(page.getByText(/Página 2 de 3/)).toBeVisible();
  await page.getByRole("button", { name: "Próxima", exact: true }).click();
  await expect(page.getByRole("button", { name: "Produto 125", exact: true })).toBeVisible();
  await page.getByLabel("Buscar no catálogo", { exact: true }).fill("Produto 125");
  await expect(page.getByRole("button", { name: /^Produto / })).toHaveCount(1);
  await page.getByLabel("Buscar no catálogo", { exact: true }).fill("");
  await page.getByRole("tab", { name: "Categorias", exact: true }).click();
  await page.getByLabel("Buscar no catálogo", { exact: true }).fill("Categoria 125");
  await expect(page.getByText("Categoria 125", { exact: true })).toBeVisible();
  await abrirMenu(page);
  await page.getByRole("link", { name: "Usuários", exact: true }).click();
  await page.getByLabel("Buscar usuário").fill("pessoa125@teste.com");
  await expect(page.getByRole("cell", { name: "pessoa125@teste.com", exact: true })).toBeVisible();
});

test("nota carrega todas as categorias e confirma com a conferência final", async ({ page }) => {
  const estado = await preparar(page);
  await page.goto("/notas-fiscais?id=1");
  await expect(page.locator("#nota-categoria-geral option")).toHaveCount(126);
  await page.locator("#nota-categoria-geral").selectOption("125");
  await page.getByLabel("Conferi os itens, os descontos e os custos rateados").check();
  await page.getByRole("button", { name: "Confirmar entrada no estoque" }).click();
  await expect(page.getByText("Esta nota já foi importada. Enviá-la novamente não duplica o estoque.")).toBeVisible();
  expect(estado.confirmacoes).toHaveLength(1);
  expect(estado.confirmacoes[0].custosConferidos).toBe(true);
  expect(estado.confirmacoes[0].itens[0].categoriaId).toBe(125);
});


test("expiração da sessão atual remove os dados e retorna ao login", async ({ page }) => {
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  estado.expirarSessao = true;
  await page.getByRole("button", { name: "Atualizar", exact: true }).click();
  await expect(page).toHaveURL(/login/);
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toHaveCount(0);
});
