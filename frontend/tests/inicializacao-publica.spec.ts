import { test, expect, type Page } from "@playwright/test";
import { preparar, abrirMenu } from "./fixtures";

async function adiarSessao(page: Page, status = 401, perfil = "ADMIN") {
  let liberar!: () => void;
  const espera = new Promise<void>(resolve => { liberar = resolve; });
  let recebeu = false;
  await page.route("**/api/auth/me", async route => {
    if (recebeu) return route.fallback();
    recebeu = true;
    await espera;
    await route.fulfill({ status, json: status === 200
      ? { id: 10, nome: "Sessão anterior", email: "anterior@teste.com", perfil, restauranteId: 1, restauranteNome: "Restaurante A" }
      : {} }).catch(() => {});
  });
  return { liberar, recebeu: () => recebeu };
}

for (const largura of [320, 1440]) {
  test(`home interativa com toda API pendente em ${largura}px`, async ({ page }) => {
    await preparar(page);
    const sessao = await adiarSessao(page);
    await page.setViewportSize({ width: largura, height: 900 });
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Menos desperdício");
    await page.getByLabel("Faturamento mensal").fill("200000");
    await expect(page.getByText("R$ 2.000")).toBeVisible();
    await page.getByRole("main").getByRole("link", { name: "Entrar", exact: true }).first().click();
    await expect(page.locator("#login-email")).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    sessao.liberar();
  });
}

for (const status of [401, 503]) test(`páginas públicas permanecem disponíveis com sessão ${status}`, async ({ page }) => {
  await preparar(page);
  await page.route("**/api/auth/me", route => route.fulfill({ status, json: {} }));
  for (const [path, titulo] of [["/", "Menos desperdício"], ["/login", "Entrar"], ["/registro", "Criar conta"], ["/sobre", "Estoque e CMV"], ["/privacidade", "Política de Privacidade"], ["/confirmar-email", "Confirmar e-mail"], ["/verificar-email", "Confira seu e-mail"]]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1 })).toContainText(titulo);
  }
});

for (const path of ["/login", "/registro"]) test(`campos de ${path} e Google sobrevivem ao bootstrap anônimo`, async ({ page }) => {
  await preparar(page);
  const sessao = await adiarSessao(page);
  await page.route("**/api/auth/google/configuracao", async route => {
    await expect.poll(sessao.recebeu).toBe(true);
    sessao.liberar();
    await route.fulfill({ json: { enabled: true } });
  });
  await page.goto(path);
  const input = page.locator(path === "/login" ? "#login-email" : "#reg-email");
  await input.fill("digitando@teste.com");
  await expect(page.getByRole("link", { name: "Continuar com Google" })).toBeVisible();
  await expect(input).toHaveValue("digitando@teste.com");
});

for (const [perfil, destino] of [["ADMIN", "/dashboard"], ["NUTRICIONISTA", "/dashboard"], ["COZINHA", "/estoque"], ["PLATAFORMA", "/plataforma"]]) test(`sessão ${perfil} mantém entrada automática`, async ({ page }) => {
  await preparar(page, perfil);
  const sessao = await adiarSessao(page, 200, perfil);
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Menos desperdício");
  sessao.liberar();
  await expect(page).toHaveURL(new RegExp(destino + "$"));
});

for (const status of [200, 401]) test(`bootstrap atrasado ${status} não apaga login nem restaura sessão depois do logout`, async ({ page }) => {
  await preparar(page);
  const sessao = await adiarSessao(page, status);
  await page.goto("/login");
  await expect.poll(sessao.recebeu).toBe(true);
  await page.locator("#login-email").fill("b@teste.com");
  await page.locator("#login-senha").fill("Senha@12345");
  await page.getByRole("button", { name: "Entrar", exact: true }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  sessao.liberar();
  await abrirMenu(page);
  await page.getByRole("link", { name: "Estoque", exact: true }).click();
  await expect(page.getByText("Ingrediente do restaurante B", { exact: true })).toBeVisible();
  await abrirMenu(page);
  await page.getByRole("button", { name: "Sair da conta" }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test("rota interna não carrega dados antes da autenticação", async ({ page }) => {
  const estado = await preparar(page);
  const sessao = await adiarSessao(page);
  await page.goto("/estoque");
  await expect(page.getByText("Carregando estoQ…")).toBeVisible();
  expect(estado.consultas.some(url => url.startsWith("/api/estoque"))).toBe(false);
  sessao.liberar();
  await expect(page).toHaveURL(/\/login$/);
});
