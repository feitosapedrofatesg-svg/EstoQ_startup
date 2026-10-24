import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

async function publico(page: Page) {
  await preparar(page);
  await page.route("**/api/auth/me", route => route.fulfill({ status: 401, json: {} }));
}

test("abrir link não confirma, token sai da barra e só o botão envia o POST", async ({ page }) => {
  await publico(page);
  const envios: Record<string, unknown>[] = [];
  const token = "A".repeat(43);
  await page.route("**/api/auth/email/confirmar", route => {
    envios.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  await page.goto(`/confirmar-email#token=${token}`);
  await expect(page.getByRole("button", { name: "Confirmar e-mail", exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/confirmar-email$/);
  expect(envios).toHaveLength(0);
  await page.getByRole("button", { name: "Confirmar e-mail", exact: true }).click();
  await expect(page.getByRole("heading", { name: "E-mail confirmado", exact: true })).toBeVisible();
  expect(envios).toEqual([{ token }]);
  await expect(page.getByRole("button", { name: "Confirmar e-mail", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Voltar para entrar" })).toHaveAttribute("href", "/login");
});

test("link vencido orienta reenvio e a tela de confirmação funciona no celular", async ({ page }) => {
  await publico(page);
  await page.setViewportSize({ width: 375, height: 812 });
  await page.route("**/api/auth/email/confirmar", route => route.fulfill({ status: 400,
    json: { message: "Link inválido, vencido ou já utilizado. Solicite outro link de confirmação." } }));
  await page.route("**/api/auth/email/reenviar", route => route.fulfill({ status: 202,
    json: { message: "Se houver uma conta aguardando confirmação, enviaremos um link. Confira também o spam." } }));
  await page.goto(`/confirmar-email#token=${"B".repeat(43)}`);
  await page.getByRole("button", { name: "Confirmar e-mail", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Link inválido");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: "/tmp/estoq-confirmacao-email-mobile.png", fullPage: true });
  await page.getByRole("link", { name: "Solicitar outro link" }).click();
  await page.getByRole("textbox", { name: "E-mail", exact: true }).fill("pessoa@teste.local");
  await page.getByRole("button", { name: "Reenviar link de confirmação" }).click();
  await expect(page.getByRole("status")).toContainText("Se houver uma conta");
  await expect(page.getByRole("button", { name: "Aguarde 1 minuto para reenviar" })).toBeDisabled();
});

test("cadastro pendente vai para confirmação e mantém e-mail preenchido", async ({ page }) => {
  await publico(page);
  await page.route("**/api/registro", route => route.fulfill({ status: 201, json: { confirmacaoEmailPendente: true } }));
  await page.goto("/registro");
  await page.locator("#reg-loja").fill("Cozinha teste");
  await page.locator("#reg-nome").fill("Pessoa teste");
  await page.locator("#reg-email").fill("pessoa@teste.local");
  await page.locator("#reg-senha").fill("Teste@12345");
  await page.locator("#reg-confirmar-senha").fill("Teste@12345");
  await page.getByRole("button", { name: "Criar conta", exact: true }).click();
  await expect(page).toHaveURL(/\/verificar-email$/);
  await expect(page.getByRole("textbox", { name: "E-mail", exact: true })).toHaveValue("pessoa@teste.local");
});

test("login pendente explica confirmação e oferece reenvio", async ({ page }) => {
  await publico(page);
  await page.route("**/api/auth/login", route => route.fulfill({ status: 403,
    json: { message: "Confirme seu e-mail pelo link enviado para entrar.", motive: "EMAIL_NAO_CONFIRMADO" } }));
  await page.goto("/login");
  await page.locator("#login-email").fill("pessoa@teste.local");
  await page.locator("#login-senha").fill("Teste@12345");
  await page.getByRole("button", { name: "Entrar", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Confirme seu e-mail");
  await page.getByRole("link", { name: "Reenviar confirmação de e-mail" }).click();
  await expect(page.getByRole("textbox", { name: "E-mail", exact: true })).toHaveValue("pessoa@teste.local");
});
