import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

for (const width of [320, 1440]) {
  test(`ajustes mostram e filtram somente quem contou em ${width}px`, async ({ page }) => {
    await preparar(page);
    await page.setViewportSize({ width, height: 1000 });
    const movimentos = [
      { id: 1, produtoNome: "Farinha", usuarioContagemId: 1, usuarioContagemNome: "Pedro" },
      { id: 2, produtoNome: "Arroz", usuarioContagemId: 2, usuarioContagemNome: "Lucas" },
      { id: 3, produtoNome: "Registro antigo", usuarioContagemId: null, usuarioContagemNome: null },
    ].map(m => ({ tipo: "AJUSTE", dataHora: "2026-11-26T15:00:00", produtoId: m.id,
      loteCodigo: "LOTE-TESTE", quantidade: -1, diferencaApurada: -1, unidadeMedida: "KG",
      usuarioId: 1, usuarioNome: "Pedro", observacao: "Ajuste do balanço #1", ...m }));
    await page.route("**/api/movimentacoes**", route => {
      const usuarioId = new URL(route.request().url()).searchParams.get("usuarioId");
      return route.fulfill({ json: movimentos.filter(m => !usuarioId || String(m.usuarioContagemId) === usuarioId) });
    });
    await page.route("**/api/usuarios**", route => route.fulfill({ json: { content: [
      { id: 1, nome: "Pedro", email: "pedro@teste.local", perfil: "ADMIN", ativo: true },
      { id: 2, nome: "Lucas", email: "lucas@teste.local", perfil: "ADMIN", ativo: true },
    ], number: 0, totalPages: 1, totalElements: 2, size: 100 } }));
    await page.goto("/movimentacoes?tipo=AJUSTE");
    const arroz = page.locator("tbody tr").filter({ hasText: "Arroz" });
    await expect(arroz.locator('td[data-label="Usuário"]')).toHaveText("Lucas");
    const antigo = page.locator("tbody tr").filter({ hasText: "Registro antigo" });
    await expect(antigo.locator('td[data-label="Usuário"]')).toHaveText("Não identificado");
    await expect(page.getByText("Aplicado por:", { exact: false })).toHaveCount(0);
    await expect(page.getByText("Usuário registrado:", { exact: false })).toHaveCount(0);
    await page.locator(".movimentacoes-usuario .searchselect__trigger").click();
    await page.getByRole("option").filter({ hasText: "lucas@teste.local" }).click();
    await expect(page.locator("tbody tr")).toHaveCount(1);
    await expect(arroz).toBeVisible();
    await page.locator(".movimentacoes-usuario .searchselect__trigger").click();
    await page.getByRole("option").filter({ hasText: "pedro@teste.local" }).click();
    await expect(page.locator("tbody tr")).toHaveCount(1);
    await expect(page.locator("tbody tr").filter({ hasText: "Farinha" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}
