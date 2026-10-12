import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

test("uma data atualiza os relatórios e os PDFs sem filtros independentes", async ({ page }) => {
  await preparar(page);
  const consultas: URL[] = [];
  await page.route("**/api/relatorios/**", async route => {
    const url = new URL(route.request().url());
    consultas.push(url);
    if (url.pathname.includes("/pdf/")) return route.fulfill({ contentType: "application/pdf", body: "%PDF-1.4" });
    if (url.pathname.endsWith("/receita-base")) return route.fulfill({ json: { receitaBase: 100 } });
    if (url.pathname === "/api/relatorios/cmv") return route.fulfill({ json: { receitaBase: 100, cmv: 20 } });
    return route.fulfill({ json: [] });
  });
  await page.goto("/relatorios");
  const data = page.getByLabel("Data", { exact: true });
  const hoje = await page.evaluate(() => {
    const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  });
  await expect(data).toHaveValue(hoje);
  await expect(page.locator('input[type="date"]')).toHaveCount(1);
  await data.fill("2026-08-15");
  for (const rota of ["cmv", "cmv/receita-base", "desperdicio", "desperdicio/agregado", "consumo/dia-semana"]) {
    await expect.poll(() => consultas.some(u => u.pathname === `/api/relatorios/${rota}` && u.searchParams.get("inicio") === "2026-08-15" && u.searchParams.get("fim") === "2026-08-15")).toBe(true);
  }
  await expect.poll(() => consultas.some(u => u.pathname.endsWith("/consumo-medio") && u.searchParams.get("dias") === "1" && u.searchParams.get("fim") === "2026-08-15")).toBe(true);
  await expect.poll(() => consultas.some(u => u.pathname.endsWith("/cmv/mensal") && u.searchParams.get("inicio") === "2026-01-01" && u.searchParams.get("fim") === "2026-08-15")).toBe(true);
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "CMV", exact: true }).click();
  expect((await download).suggestedFilename()).toBe("estoQ-cmv-2026-08-15.pdf");
  const pdf = consultas.find(u => u.pathname.endsWith("/pdf/cmv"));
  expect(pdf?.searchParams.get("inicio")).toBe("2026-08-15");
  expect(pdf?.searchParams.get("fim")).toBe("2026-08-15");
  expect(pdf?.searchParams.get("receitaBase")).toBe("100");
  await page.getByRole("button", { name: "Hoje", exact: true }).click();
  await expect(data).toHaveValue(hoje);
});
