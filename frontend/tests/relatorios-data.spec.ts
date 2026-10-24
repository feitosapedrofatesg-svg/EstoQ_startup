import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

test("um período atualiza os relatórios e os PDFs sem filtros independentes", async ({ page }) => {
  await preparar(page);
  const consultas: URL[] = [];
  await page.route("**/api/relatorios/**", async route => {
    const url = new URL(route.request().url());
    consultas.push(url);
    if (url.pathname.includes("/exportar") || url.pathname.includes("/pdf/")) return route.fulfill({ contentType: "application/pdf", body: "%PDF-1.4" });
    if (url.pathname.endsWith("/faturamento-diario")) return route.fulfill({ json: { receitaBase: 100,
      totalInformado: 0, diasInformados: 0, diasPendentes: 1, receitaPorPeriodo: true, dias: [] } });
    if (url.pathname === "/api/relatorios/cmv") return route.fulfill({ json: { receitaBase: 100, cmv: 20 } });
    return route.fulfill({ json: [] });
  });
  await page.goto("/relatorios");
  const inicio = page.getByLabel("De", { exact: true });
  const fim = page.getByLabel("Até", { exact: true });
  const hoje = await page.evaluate(() => {
    const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  });
  await expect(inicio).toHaveValue(hoje);
  await expect(fim).toHaveValue(hoje);
  await expect(page.locator('input[type="date"]')).toHaveCount(3);
  await inicio.fill("2026-08-10");
  await fim.fill("2026-08-15");
  for (const rota of ["cmv", "faturamento-diario", "desperdicio", "desperdicio/agregado", "consumo/dia-semana"]) {
    await expect.poll(() => consultas.some(u => u.pathname === `/api/relatorios/${rota}` && u.searchParams.get("inicio") === "2026-08-10" && u.searchParams.get("fim") === "2026-08-15")).toBe(true);
  }
  await expect.poll(() => consultas.some(u => u.pathname.endsWith("/consumo-medio") && u.searchParams.get("dias") === "6" && u.searchParams.get("fim") === "2026-08-15")).toBe(true);
  await expect.poll(() => consultas.some(u => u.pathname.endsWith("/cmv/mensal") && u.searchParams.get("inicio") === "2026-08-10" && u.searchParams.get("fim") === "2026-08-15")).toBe(true);
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Exportar relatório", exact: true }).click();
  await page.locator("#export-tipo").selectOption("cmv");
  await page.locator("#export-formato").selectOption("pdf");
  await page.getByRole("button", { name: "Exportar PDF", exact: true }).click();
  expect((await download).suggestedFilename()).toBe("estoQ-cmv-2026-08-10-a-2026-08-15.pdf");
  const pdf = consultas.find(u => u.pathname.endsWith("/exportar"));
  expect(pdf?.searchParams.get("tipo")).toBe("cmv");
  expect(pdf?.searchParams.get("formato")).toBe("pdf");
  expect(pdf?.searchParams.get("inicio")).toBe("2026-08-10");
  expect(pdf?.searchParams.get("fim")).toBe("2026-08-15");
  expect(pdf?.searchParams.has("receitaBase")).toBe(false);
  await expect(page.getByRole("cell", { name: "Faturamento usado no cálculo", exact: true })).toBeVisible();
  for (const [tipo, label] of [["faturamento", "Faturamento"], ["cmv-mensal", "CMV mensal"],
    ["produtos-abertos", "Embalagens abertas"], ["consumo-dia-semana", "Consumo por dia da semana"]]) {
    const arquivo = page.waitForEvent("download");
    await page.getByRole("button", { name: "Exportar relatório", exact: true }).click();
    await page.locator("#export-tipo").selectOption(tipo);
    await page.getByRole("button", { name: "Exportar PDF", exact: true }).click();
    expect((await arquivo).suggestedFilename()).toBe(`estoQ-${tipo}-2026-08-10-a-2026-08-15.pdf`);
    const consulta = consultas.findLast ? consultas.findLast(u => u.pathname.endsWith("/exportar")) : [...consultas].reverse().find(u => u.pathname.endsWith("/exportar"));
    expect(consulta?.searchParams.get("tipo")).toBe(tipo);
    expect(consulta?.searchParams.get("inicio")).toBe("2026-08-10");
    expect(consulta?.searchParams.get("fim")).toBe("2026-08-15");
    expect(consulta?.searchParams.get("dias")).toBe("6");
  }
});
