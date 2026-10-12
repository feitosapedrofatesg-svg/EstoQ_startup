import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

test("visto remove o alerta confirmado e cliques repetidos não marcam outros", async ({ page }) => {
  await preparar(page);
  let liberar: (() => Promise<void>) | undefined;
  const gravados: number[] = [];
  const alertas = [1, 2, 3].map(id => ({ id, mensagem: `Alerta ${id}`, dataGeracao: "2026-10-12T10:00:00", visualizado: false }));
  await page.route("**/api/alertas**", async route => {
    const url = new URL(route.request().url());
    if (route.request().method() === "PUT") {
      const id = Number(url.pathname.split("/")[3]); gravados.push(id);
      return new Promise<void>(resolve => { liberar = async () => { await route.fulfill({ json: { id, visualizado: true } }); resolve(); }; });
    }
    if (url.pathname.endsWith("abertos")) return route.fulfill({ json: 3 });
    // Simula também uma resposta de leitura atrasada contendo o alerta já confirmado.
    return route.fulfill({ json: alertas });
  });
  await page.goto("/estoque");
  await page.getByRole("button", { name: "Alertas, 3 novos" }).click();
  const painel = page.getByRole("dialog", { name: "Central de alertas" });
  await expect(painel.getByText("Alerta 1", { exact: true })).toBeVisible();
  const primeiro = painel.getByRole("button", { name: "Marcar como visto" }).first();
  await primeiro.click();
  await expect.poll(() => gravados.length).toBe(1);
  await expect(painel.getByText("Alerta 1", { exact: true })).toHaveCount(0);
  await expect(painel.getByRole("button", { name: "Marcar como visto" }).first()).toBeDisabled();
  for (let i = 0; i < 3; i++) await painel.getByRole("button", { name: "Marcar como visto" }).first().evaluate(botao => (botao as HTMLButtonElement).click());
  expect(gravados).toEqual([1]);
  await liberar!();
  await expect(painel.getByText("Alerta 1", { exact: true })).toHaveCount(0);
  await expect(painel.getByText("Alerta 2", { exact: true })).toBeVisible();
  await expect(painel.getByText("Alerta 3", { exact: true })).toBeVisible();
  expect(gravados).toEqual([1]);
  await expect(painel.getByRole("button", { name: "Marcar como visto" }).first()).toBeEnabled();
});

test("falha ao marcar visto preserva o alerta para nova tentativa", async ({ page }) => {
  await preparar(page);
  await page.route("**/api/alertas**", async route => {
    if (route.request().method() === "PUT") return route.fulfill({ status: 503, json: { message: "Falha de teste" } });
    const url = new URL(route.request().url());
    return route.fulfill({ json: url.pathname.endsWith("abertos") ? 1 : [{ id: 1, mensagem: "Alerta pendente", dataGeracao: "2026-10-12T10:00:00" }] });
  });
  await page.goto("/estoque"); await page.getByRole("button", { name: "Alertas, 1 novos" }).click();
  await page.getByRole("button", { name: "Marcar como visto" }).click();
  await expect(page.getByText("Falha de teste", { exact: true })).toBeVisible();
  await expect(page.getByText("Alerta pendente", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Marcar como visto" })).toBeEnabled();
});
