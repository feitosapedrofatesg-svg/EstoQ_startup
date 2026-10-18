import { test, expect } from "@playwright/test";

for (const status of [401, 429, 502, 503, 504]) {
  test(`login distingue credenciais, limite e indisponibilidade: ${status}`, async ({ page }) => {
    await page.route("**/api/auth/me", route => route.fulfill({ status: 401, json: {} }));
    await page.route("**/api/auth/csrf", route => route.fulfill({
      json: { token: "teste", headerName: "X-CSRF-TOKEN" },
    }));
    await page.route("**/api/auth/login", route => route.fulfill({ status, body: "Falha do teste" }));
    await page.goto("/login");
    await page.getByRole("textbox", { name: "E-mail", exact: true }).fill("acesso@test.local");
    await page.locator("#login-senha").fill("Teste@12345");
    await page.getByRole("button", { name: "Entrar", exact: true }).click();
    const mensagem = status === 401 ? "E-mail ou senha incorretos."
      : status === 429 ? "Muitas tentativas de acesso. Espere alguns minutos e tente novamente."
      : "Serviço de acesso temporariamente indisponível. Tente novamente mais tarde.";
    await expect(page.getByRole("alert")).toHaveText(mensagem);
    await expect(page).toHaveURL(/\/login$/);
  });
}
