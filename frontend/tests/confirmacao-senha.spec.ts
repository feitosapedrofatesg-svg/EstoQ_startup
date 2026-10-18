import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

for (const senha of ["Teste@12345", "Teste@12345 "]) {
  test(`cadastro só envia senhas iguais, preservando espaços: ${senha.length}`, async ({ page }) => {
    const envios: Record<string, unknown>[] = [];
    await page.route("**/api/auth/me", route => route.fulfill({ status: 401, json: {} }));
    await page.route("**/api/auth/csrf", route => route.fulfill({
      json: { token: "teste", headerName: "X-CSRF-TOKEN" },
    }));
    await page.route("**/api/registro", route => {
      envios.push(route.request().postDataJSON());
      return route.fulfill({ status: 204 });
    });
    await page.goto("/registro");
    await page.locator("#reg-loja").fill("Cozinha do teste");
    await page.locator("#reg-nome").fill("Responsável do teste");
    await page.locator("#reg-email").fill("cadastro@teste.local");
    await page.locator("#reg-senha").fill(senha);
    await page.getByRole("button", { name: "Criar conta", exact: true }).click();
    await expect(page.getByRole("alert")).toHaveText("As senhas não conferem.");
    expect(envios).toHaveLength(0);
    await page.locator("#reg-confirmar-senha").fill("Outra@12345");
    await page.getByRole("button", { name: "Criar conta", exact: true }).click();
    expect(envios).toHaveLength(0);
    await page.locator("#reg-confirmar-senha").fill(senha);
    await page.getByRole("button", { name: "Criar conta", exact: true }).click();
    await expect(page).toHaveURL(/\/login$/);
    expect(envios).toEqual([{ nomeLoja: "Cozinha do teste", nomeResponsavel: "Responsável do teste",
      email: "cadastro@teste.local", senha }]);
  });
}

test("novo usuário exige confirmação antes de gravar", async ({ page }) => {
  const envios: Record<string, unknown>[] = [];
  await preparar(page);
  await page.route("**/api/usuarios", route => {
    if (route.request().method() !== "POST") return route.fallback();
    envios.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  await page.goto("/usuarios");
  await page.getByRole("button", { name: "Novo usuário", exact: true }).click();
  const modal = page.getByRole("dialog");
  await modal.getByPlaceholder("Nome e sobrenome").fill("Operador");
  await modal.getByPlaceholder("pessoa@restaurante.com").fill("operador@teste.local");
  await modal.locator("#usuario-senha").fill("Teste@12345");
  await modal.getByRole("button", { name: "Salvar", exact: true }).click();
  await expect(modal.getByRole("alert")).toHaveText("As senhas não conferem.");
  expect(envios).toHaveLength(0);
  await modal.locator("#usuario-confirmar-senha").fill("Teste@12345");
  await modal.getByRole("button", { name: "Salvar", exact: true }).click();
  await expect(modal).toHaveCount(0);
  expect(envios).toEqual([{ nome: "Operador", email: "operador@teste.local", perfil: "COZINHA", senha: "Teste@12345" }]);
});

test("edição mantém senha com campos vazios e exige confirmação ao trocar", async ({ page }) => {
  const envios: Record<string, unknown>[] = [];
  await preparar(page);
  await page.route("**/api/usuarios/1", route => {
    envios.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  await page.goto("/usuarios");
  await page.getByRole("button", { name: "Editar", exact: true }).first().click();
  const modal = page.getByRole("dialog");
  await modal.getByRole("button", { name: "Salvar", exact: true }).click();
  await expect(modal).toHaveCount(0);
  expect(envios).toHaveLength(1);
  expect(envios[0].senha).toBeNull();
  await page.getByRole("button", { name: "Editar", exact: true }).first().click();
  // Confirmação isolada também não pode provocar uma atualização silenciosa.
  await modal.locator("#usuario-confirmar-senha").fill("Teste@12345");
  await modal.getByRole("button", { name: "Salvar", exact: true }).click();
  await expect(modal.getByRole("alert")).toHaveText("As senhas não conferem.");
  expect(envios).toHaveLength(1);
  await modal.locator("#usuario-senha").fill("Nova@12345");
  await modal.getByRole("button", { name: "Salvar", exact: true }).click();
  expect(envios).toHaveLength(1);
  await modal.locator("#usuario-confirmar-senha").fill("Nova@12345");
  await modal.getByRole("button", { name: "Salvar", exact: true }).click();
  await expect(modal).toHaveCount(0);
  expect(envios).toHaveLength(2);
  expect(envios[1].senha).toBe("Nova@12345");
  expect(envios[1]).not.toHaveProperty("confirmarSenha");
  await page.getByRole("button", { name: "Editar", exact: true }).first().click();
  await expect(modal.locator("#usuario-senha")).toHaveValue("");
  await expect(modal.locator("#usuario-confirmar-senha")).toHaveValue("");
});

test("plataforma só redefine a senha após confirmação igual", async ({ page }) => {
  const envios: Record<string, unknown>[] = [];
  await preparar(page, "PLATAFORMA");
  await page.route("**/api/plataforma/restaurantes", route => route.fulfill({ json: [{
    id: 1, nome: "Cozinha do teste", ativo: true, totalUsuarios: 1, dataHoraCriacao: "2026-10-18T12:00:00",
  }] }));
  await page.route("**/api/plataforma/restaurantes/1/redefinir-admin", route => {
    envios.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  await page.goto("/plataforma");
  await page.getByRole("button", { name: "Maximizar Cozinhas", exact: true }).click();
  await page.getByRole("button", { name: "Redefinir senha", exact: true }).click();
  const modal = page.getByRole("dialog");
  await modal.locator("#plataforma-senha").fill("Teste@12345");
  await modal.getByRole("button", { name: "Salvar nova senha", exact: true }).click();
  await expect(modal.getByRole("alert")).toHaveText("As senhas não conferem.");
  expect(envios).toHaveLength(0);
  await modal.locator("#plataforma-confirmar-senha").fill("Outra@12345");
  await modal.getByRole("button", { name: "Salvar nova senha", exact: true }).click();
  expect(envios).toHaveLength(0);
  await modal.locator("#plataforma-confirmar-senha").fill("Teste@12345");
  await modal.getByRole("button", { name: "Salvar nova senha", exact: true }).click();
  await expect(modal).toHaveCount(0);
  expect(envios).toEqual([{ senha: "Teste@12345" }]);
});
