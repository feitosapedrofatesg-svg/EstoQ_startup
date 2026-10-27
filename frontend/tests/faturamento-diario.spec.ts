import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

async function prepararCmv(page: Page) {
  await preparar(page);
  await page.clock.install({ time: new Date("2026-10-19T12:00:00Z") });
  await page.addInitScript(() => Object.defineProperty(window, "EventSource", {
    value: class extends EventTarget { close() {} },
  }));
  const estado = { dias: new Map<string, { valor: number | null; version: number | null }>(),
    envios: [] as { data: string; valor: number | null; version: number | null }[],
    legado: null as number | null, custoZero: false, consultas: [] as URL[] };
  for (let i = 0; i < 30; i++) {
    const d = new Date("2026-09-20T12:00:00Z"); d.setUTCDate(d.getUTCDate() + i);
    estado.dias.set(d.toISOString().slice(0, 10), { valor: 100, version: 0 });
  }
  function periodo(url: URL) {
    const inicio = url.searchParams.get("inicio") ?? "2026-09-20";
    const fim = url.searchParams.get("fim") ?? "2026-10-19";
    const dias = [];
    for (const d = new Date(inicio + "T12:00:00Z"); d.toISOString().slice(0, 10) <= fim; d.setUTCDate(d.getUTCDate() + 1)) {
      const data = d.toISOString().slice(0, 10);
      dias.push({ data, ...(estado.dias.get(data) ?? { valor: null, version: null }) });
    }
    const diasInformados = dias.filter(d => d.valor !== null).length;
    const totalInformado = dias.reduce((s, d) => s + (d.valor ?? 0), 0);
    const diasPendentes = dias.length - diasInformados;
    const legado = inicio === "2026-09-20" && fim === "2026-10-19" ? estado.legado : null;
    return { inicio, fim, dias, diasInformados, totalInformado, diasPendentes,
      receitaBase: diasPendentes === 0 ? totalInformado : legado, receitaPorPeriodo: diasPendentes > 0 && legado !== null };
  }
  function cmv(url: URL) {
    const p = periodo(url); const custo = estado.custoZero ? 0 : p.dias.length * 30;
    return { cmv: custo, receitaBase: p.receitaBase, cmvPercentual: p.receitaBase ? custo / p.receitaBase * 100 : null,
      faturamentoInformado: p.totalInformado, diasFaturamentoPendentes: p.diasPendentes,
      receitaPorPeriodo: p.receitaPorPeriodo, percentualIdeal: 30, diferencaPercentualParaMeta: 0,
      valorEstoqueInicial: 0, valorCompras: custo, valorEstoqueFinal: 0, valorConsumoRegistrado: custo,
      valorDesperdicio: 0, percentualDesperdicioSobreCmv: 0, valorPerdasNaoExplicadas: 0, valorAjustesPositivosBalancos: 0 };
  }
  await page.route("**/api/relatorios/**", async route => {
    const url = new URL(route.request().url()); estado.consultas.push(url);
    if (url.pathname === "/api/relatorios/faturamento-diario") {
      if (route.request().method() === "GET") return route.fulfill({ json: periodo(url) });
      const envio = route.request().postDataJSON(); estado.envios.push(envio);
      const anterior = estado.dias.get(envio.data);
      if (envio.version !== (anterior?.version ?? null)) return route.fulfill({ status: 409,
        json: { message: "O faturamento deste dia mudou. Recarregue e confira antes de salvar." } });
      const salvo = { valor: envio.valor, version: anterior?.version == null ? 0 : anterior.version + 1 };
      estado.dias.set(envio.data, salvo);
      return route.fulfill({ json: { data: envio.data, ...salvo } });
    }
    if (url.pathname === "/api/relatorios/cmv") return route.fulfill({ json: cmv(url) });
    if (url.pathname.includes("/pdf/") || url.pathname.endsWith("/exportar")) return route.fulfill({ contentType: "application/pdf", body: "%PDF-1.4" });
    return route.fallback();
  });
  await page.route("**/api/dashboard/resumo**", route => {
    const url = new URL(route.request().url()); estado.consultas.push(url); const c = cmv(url);
    return route.fulfill({ json: { ...c, cmvPeriodo: c.cmv, cmvIdeal: 30, diferencaCmvParaMeta: 0,
      totalProdutosAtivos: 1, produtosEstoqueBaixo: 0, lotesProximosVencimento: 0, lotesVencidos: 0,
      produtosAbertosAtivos: 0, balancosPendentes: 0, valorDesperdicioPeriodo: 0, perdasNaoExplicadasPeriodo: 0 } });
  });
  return estado;
}

test("salvar o dia completa os 30 dias e mantém o percentual ao abrir relatório e PDF", async ({ page }) => {
  const estado = await prepararCmv(page);
  estado.dias.delete("2026-10-19");
  await page.goto("/dashboard");
  await expect(page.getByText(/Percentual pendente: faltam 1 dia/)).toBeVisible();
  await page.locator("#faturamento-valor").fill("100,00");
  await page.getByRole("button", { name: "Salvar faturamento", exact: true }).click();
  await expect(page.getByLabel("CMV percentual dos últimos 30 dias")).toHaveText("30,0%");
  expect(estado.envios).toEqual([{ data: "2026-10-19", valor: 100, version: null }]);
  await page.getByRole("link", { name: "Ver relatório destes 30 dias", exact: true }).click();
  await expect(page.getByLabel("De", { exact: true })).toHaveValue("2026-09-20");
  await expect(page.getByLabel("Até", { exact: true })).toHaveValue("2026-10-19");
  await expect(page.getByRole("img", { name: "Percentual de CMV: 30%, meta 30%", exact: true })).toBeVisible();
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Exportar relatório", exact: true }).click();
  await page.locator("#export-tipo").selectOption("cmv");
  await page.locator("#export-formato").selectOption("pdf");
  await page.getByRole("button", { name: "Exportar PDF", exact: true }).click();
  await download;
  const pdf = estado.consultas.find(u => u.pathname === "/api/relatorios/exportar" && u.searchParams.get("tipo") === "cmv");
  expect(pdf?.searchParams.get("inicio")).toBe("2026-09-20");
  expect(pdf?.searchParams.get("fim")).toBe("2026-10-19");
  expect(pdf?.searchParams.has("receitaBase")).toBe(false);
});

test("relatórios iniciam em hoje, distinguem zero de pendência e permitem corrigir sem duplicar", async ({ page }) => {
  const estado = await prepararCmv(page);
  await page.goto("/relatorios");
  await expect(page.getByLabel("De", { exact: true })).toHaveValue("2026-10-19");
  await expect(page.getByLabel("Até", { exact: true })).toHaveValue("2026-10-19");
  await expect(page.locator("#faturamento-valor")).toHaveValue("100");
  await page.locator("#faturamento-valor").fill("0");
  await page.getByRole("button", { name: "Salvar faturamento", exact: true }).click();
  await expect(page.getByText(/Faturamento total igual a zero/)).toBeVisible();
  await page.getByRole("button", { name: "Ver dias do período", exact: true }).click();
  await expect(page.getByRole("table", { name: "Faturamento por data", exact: true }).locator("tbody tr")).toHaveCount(1);
  await page.reload();
  await expect(page.locator("#faturamento-valor")).toHaveValue("0");
  await page.locator("#faturamento-valor").fill("");
  await page.getByRole("button", { name: "Salvar faturamento", exact: true }).click();
  await expect(page.getByText(/Percentual pendente: faltam 1 dia/)).toBeVisible();
  expect(estado.envios).toEqual([{ data: "2026-10-19", valor: 0, version: 0 }, { data: "2026-10-19", valor: null, version: 1 }]);
  expect(estado.dias.size).toBe(30);
});

for (const largura of [320, 1440]) {
  test(`ver dias do período tem borda visível e abre pelo teclado em ${largura}px`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: 1000 });
    await page.emulateMedia({ reducedMotion: "reduce" });
    const estado = await prepararCmv(page);
    await page.goto("/relatorios");
    const botao = page.getByRole("button", { name: "Ver dias do período", exact: true });
    await expect(botao).toBeVisible();
    await expect(botao).toHaveClass(/btn--outline/);
    await expect(botao).toHaveCSS("border-top-color", "rgb(117, 125, 131)");
    await expect(botao).toHaveCSS("color", "rgb(39, 41, 50)");
    await expect(botao).toHaveAttribute("aria-expanded", "false");
    expect((await botao.boundingBox())!.height).toBeGreaterThanOrEqual(44);
    await expect(botao.locator("svg")).toHaveAttribute("aria-hidden", "true");
    await page.getByRole("region", { name: "Faturamento diário", exact: true }).screenshot({
      path: `/tmp/opencode/faturamento-botao-${largura}.png`,
    });

    await botao.focus();
    await botao.press("Enter");
    const ocultar = page.getByRole("button", { name: "Ocultar dias do período", exact: true });
    await expect(ocultar).toHaveAttribute("aria-expanded", "true");
    await expect(ocultar.locator("svg")).toHaveCSS("transform", "matrix(-1, 0, 0, -1, 0, 0)");
    await expect(page.getByRole("table", { name: "Faturamento por data", exact: true })).toBeVisible();
    await ocultar.press("Space");
    await expect(botao).toHaveAttribute("aria-expanded", "false");
    await expect(page.getByRole("table", { name: "Faturamento por data", exact: true })).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    expect(estado.envios).toHaveLength(0);
  });
}

test("atualização em segundo plano preserva rascunho e versão para detectar conflito", async ({ page }) => {
  const estado = await prepararCmv(page);
  await page.goto("/relatorios");
  await expect(page.locator("#faturamento-valor")).toHaveValue("100");
  await page.locator("#faturamento-valor").fill("150");
  estado.dias.set("2026-10-19", { valor: 900, version: 1 });
  await page.clock.runFor(65_000);
  await expect(page.locator("#faturamento-valor")).toHaveValue("150");
  await page.getByRole("button", { name: "Salvar faturamento", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("O faturamento deste dia mudou");
  expect(estado.envios[0].version).toBe(0);
  await expect(page.locator("#faturamento-valor")).toHaveValue("150");
  await page.getByRole("button", { name: "Recarregar faturamento", exact: true }).click();
  await expect(page.locator("#faturamento-valor")).toHaveValue("900");
  await page.locator("#faturamento-valor").fill("200");
  await page.getByRole("button", { name: "Salvar faturamento", exact: true }).click();
  await expect.poll(() => estado.envios.length).toBe(2);
  expect(estado.envios[1].version).toBe(1);
});

test("zero por cento aparece e faturamento negativo não é enviado", async ({ page }) => {
  const estado = await prepararCmv(page); estado.custoZero = true;
  await page.goto("/dashboard");
  await expect(page.getByLabel("CMV percentual dos últimos 30 dias")).toHaveText("0,0%");
  await expect(page.locator(".gauge__legend").getByText("0%", { exact: true })).toBeVisible();
  await page.locator("#faturamento-valor").fill("-1");
  await page.getByRole("button", { name: "Salvar faturamento", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("maior ou igual a zero");
  expect(estado.envios).toHaveLength(0);
});

test("falha ao consultar totais depois de salvar preserva o valor confirmado", async ({ page }) => {
  const estado = await prepararCmv(page);
  await page.goto("/relatorios");
  await expect(page.locator("#faturamento-valor")).toHaveValue("100");
  await page.route("**/api/relatorios/faturamento-diario?**", route => route.fulfill({ status: 503,
    json: { message: "Consulta indisponível no momento." } }));
  await page.locator("#faturamento-valor").fill("250");
  await page.getByRole("button", { name: "Salvar faturamento", exact: true }).click();
  await expect(page.getByText(/Não foi possível carregar o faturamento/)).toBeVisible();
  await expect(page.locator("#faturamento-valor")).toHaveValue("250");
  expect(estado.dias.get("2026-10-19")).toEqual({ valor: 250, version: 1 });
});

test("base histórica de período fica identificada sem preencher os dias com valores inventados", async ({ page }) => {
  const estado = await prepararCmv(page); estado.dias.clear(); estado.legado = 3000;
  await page.goto("/dashboard");
  await expect(page.getByLabel("CMV percentual dos últimos 30 dias")).toHaveText("30,0%");
  await expect(page.getByText(/O percentual usa o total informado anteriormente/)).toBeVisible();
  await expect(page.locator("#faturamento-valor")).toHaveValue("");
  await expect(page.getByText(/0 de 30 dias informados/)).toBeVisible();
});
