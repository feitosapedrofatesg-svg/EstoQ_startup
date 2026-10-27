import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

async function publico(page: Page, enabled = true) {
  await preparar(page);
  await page.route("**/api/auth/me", route => route.fulfill({ status: 401, json: {} }));
  await page.route("**/api/auth/google/configuracao", route => route.fulfill({ json: { enabled } }));
}
async function pendencia(page: Page, etapa = "CADASTRO") {
  await publico(page);
  await page.route("**/api/auth/google/pendente", route => route.fulfill({ json: { etapa, nome: "Pessoa Google", email: "pessoa@gmail.com" } }));
}

test("Google aparece nas duas telas públicas e desligado preserva o formulário local", async ({ page }) => {
  await publico(page);
  for (const tela of ["/login", "/registro"]) {
    await page.goto(tela);
    const google = page.getByRole("link", { name: "Continuar com Google" });
    await expect(google).toHaveAttribute("href", "/api/oauth2/authorization/google");
    expect(await google.evaluate(el => el.closest("button") === null)).toBe(true);
    await expect(page.locator('input[type="password"]').first()).toBeVisible();
  }
  await page.route("**/api/auth/google/configuracao", route => route.fulfill({ json: { enabled: false } }));
  await page.goto("/login");
  await expect(page.getByRole("link", { name: "Continuar com Google" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Entrar", exact: true })).toBeVisible();
});

test("novo cadastro Google envia somente os nomes e abre a cozinha sem pedir senha", async ({ page }) => {
  await pendencia(page);
  const envios: Record<string, unknown>[] = [];
  await page.route("**/api/registro/google", route => {
    envios.push(route.request().postDataJSON());
    expect(route.request().headers()["x-csrf-token"]).toBe("teste");
    return route.fulfill({ status: 201, json: { confirmacaoEmailPendente: false, email: "pessoa@gmail.com",
      usuario: { id: 20, nome: "Pessoa Google", email: "pessoa@gmail.com", perfil: "ADMIN", restauranteId: 20, restauranteNome: "Cozinha Google" } } });
  });
  await page.goto("/registro/google");
  await expect(page.getByLabel("E-mail", { exact: true })).toHaveValue("pessoa@gmail.com");
  await expect(page.getByLabel("E-mail", { exact: true })).toHaveJSProperty("readOnly", true);
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: "Seu nome", exact: true })).toHaveValue("Pessoa Google");
  await page.getByLabel("Nome da cozinha").fill("Cozinha Google");
  await page.getByRole("button", { name: "Criar minha cozinha" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  expect(envios).toEqual([{ nomeLoja: "Cozinha Google", nomeResponsavel: "Pessoa Google" }]);
  expect(await page.evaluate(() => Object.keys(localStorage).some(k => /google|token/i.test(k)))).toBe(false);
});

test("vincular conta existente exige a senha do EstoQ e permite corrigir uma tentativa", async ({ page }) => {
  await pendencia(page, "VINCULAR");
  let conectado = false;
  await page.route("**/api/auth/me", route => conectado
    ? route.fulfill({ json: { id: 2, nome: "Pessoa Google", email: "pessoa@gmail.com", perfil: "COZINHA", restauranteId: 2, restauranteNome: "Cozinha existente" } })
    : route.fulfill({ status: 401, json: {} }));
  let chamadas = 0;
  await page.route("**/api/auth/google/vincular", route => {
    chamadas++;
    expect(route.request().postDataJSON()).toEqual({ senha: chamadas === 1 ? "errada" : "Senha@12345" });
    return chamadas === 1 ? route.fulfill({ status: 401, json: { message: "E-mail ou senha inválidos." } })
      : (conectado = true, route.fulfill({ json: { confirmacaoEmailPendente: false, email: "pessoa@gmail.com",
        usuario: { id: 2, nome: "Pessoa Google", email: "pessoa@gmail.com", perfil: "COZINHA", restauranteId: 2, restauranteNome: "Cozinha existente" } } }));
  });
  await page.goto("/registro/google");
  await expect(page.getByRole("heading", { name: "Vincular conta Google" })).toBeVisible();
  await expect(page.getByLabel("Nome da cozinha")).toHaveCount(0);
  await page.getByLabel("Senha atual do EstoQ").fill("errada");
  await page.getByRole("button", { name: "Vincular e entrar" }).click();
  await expect(page.getByRole("alert")).toContainText("senha inválidos");
  await page.getByLabel("Senha atual do EstoQ").fill("Senha@12345");
  await page.getByRole("button", { name: "Vincular e entrar" }).click();
  await expect(page).toHaveURL(/\/estoque$/);
  expect(chamadas).toBe(2);
});

test("endereço pendente de confirmação usa a tela de reenvio", async ({ page }) => {
  await pendencia(page, "CONFIRMAR_EMAIL");
  await page.goto("/registro/google");
  await expect(page).toHaveURL(/\/verificar-email$/);
  await expect(page.getByRole("textbox", { name: "E-mail", exact: true })).toHaveValue("pessoa@gmail.com");
});

test("cancelamento e pendência vencida orientam um novo acesso", async ({ page }) => {
  await pendencia(page);
  let cancelou = false;
  await page.route("**/api/auth/google/cancelar", route => { cancelou = true; return route.fulfill({ status: 204 }); });
  await page.goto("/registro/google");
  await page.getByRole("button", { name: "Cancelar", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/); expect(cancelou).toBe(true);
  await page.route("**/api/auth/google/pendente", route => route.fulfill({ status: 400,
    json: { message: "O acesso com Google expirou. Clique em Continuar com Google novamente." } }));
  await page.goto("/registro/google");
  await expect(page.getByRole("alert")).toContainText("expirou");
  await expect(page.getByRole("link", { name: "Voltar e tentar novamente" })).toHaveAttribute("href", "/login");
  await page.goto("/login?google=cancelado");
  await expect(page.getByRole("alert")).toContainText("cancelado");
});

test("Google e conclusão de cadastro cabem no celular e no desktop", async ({ page }) => {
  await pendencia(page);
  for (const largura of [320, 375, 768, 1440]) {
    await page.setViewportSize({ width: largura, height: 900 });
    for (const tela of ["/login", "/registro/google"]) {
      await page.goto(tela);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      if (tela === "/login") {
        const google = page.getByRole("link", { name: "Continuar com Google" });
        await expect(google).toBeVisible();
        expect((await google.boundingBox())!.height).toBeGreaterThanOrEqual(44);
      }
      if (largura === 320 || largura === 1440)
        await page.screenshot({ path: `/tmp/estoq-google-${tela === "/login" ? "login" : "cadastro"}-${largura}.png`, fullPage: true });
    }
  }
});
