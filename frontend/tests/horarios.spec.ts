import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

test.use({ timezoneId: "America/Bahia" });

test("a central de alertas mostra 16:26 quando o servidor envia 19:26 UTC", async ({ page }) => {
  await preparar(page);
  await page.route("**/api/alertas**", route => route.fulfill({ json:
    route.request().url().endsWith("abertos") ? 1 : [{ id: 1, mensagem: "Aviso de horário",
      dataGeracao: "2026-10-12T19:26:00", visualizado: false }],
  }));
  await page.goto("/estoque");
  await page.getByRole("button", { name: "Alertas, 1 novos" }).click();
  const painel = page.getByRole("dialog", { name: "Central de alertas" });
  await expect(painel.getByText(/12\/10\/2026.*16:26/)).toBeVisible();
});

test("horários UTC aparecem no fuso local, inclusive na virada do dia", async ({ page }) => {
  await preparar(page);
  await page.goto("/estoque");
  const resultado = await page.evaluate(async () => {
    const { fmtDateTime, fmtDate } = await import("/src/lib/format.ts");
    return {
      semFuso: fmtDateTime("2026-10-12T19:26:00"),
      utc: fmtDateTime("2026-10-12T19:26:00Z"),
      local: fmtDateTime("2026-10-12T16:26:00-03:00"),
      virada: fmtDateTime("2026-10-13T01:30:00.123456"),
      validade: fmtDate("2026-10-12"),
      invalido: fmtDateTime("inválido"),
    };
  });
  for (const valor of [resultado.semFuso, resultado.utc, resultado.local]) {
    expect(valor).toContain("12/10/2026");
    expect(valor).toContain("16:26");
  }
  expect(resultado.virada).toContain("12/10/2026");
  expect(resultado.virada).toContain("22:30");
  expect(resultado.validade).toBe("12/10/2026");
  expect(resultado.invalido).toBe("—");
});
