import { test, expect, type Page } from "@playwright/test";
import { preparar, entrarNaLojaB } from "./fixtures";

/** Controla os avisos; a conexão HTTP real é coberta no backend com PostgreSQL. */
async function controlarAvisos(page: Page) {
  await page.addInitScript(() => {
    const fontes: any[] = [];
    (window as any).fontesTeste = fontes;
    class Fonte extends EventTarget {
      fechada = false;
      onerror: (() => void) | null = null;
      constructor(public url: string) { super(); fontes.push(this); }
      close() { this.fechada = true; }
      emitir(nome: string, grupos: string[] = []) {
        this.dispatchEvent(new MessageEvent(nome, { data: JSON.stringify({ grupos }) }));
      }
    }
    (window as any).EventSource = Fonte;
  });
}
async function avisar(page: Page, grupos: string[], nome = "alteracao") {
  await page.evaluate(({ grupos, nome }) => (window as any).fontesTeste.at(-1).emitir(nome, grupos), { grupos, nome });
}
async function prepararBalanco(page: Page) {
  await controlarAvisos(page);
  await preparar(page, "COZINHA");
  const estado = { rejeitar: false, gravacoes: [] as any[], balanco: {
    id: 1, version: 1, ativo: true, tipo: "GERAL", status: "EM_ANDAMENTO", dataHora: "2026-10-12T10:00:00",
    usuarioNome: "Cozinha", categoriaNomes: [], itens: [{ id: 1, version: 1, produtoId: 1,
      produtoNome: "Arroz", unidadeMedida: "KG", quantidadeSistema: 10, quantidadeFisica: 8,
      diferenca: -2, ajusteAplicado: false }],
  } };
  await page.route("**/api/balancos**", async route => {
    const caminho = new URL(route.request().url()).pathname;
    if (caminho === "/api/balancos/proximo") return route.fulfill({ json: [] });
    if (route.request().method() === "PUT") {
      const corpo = route.request().postDataJSON(); estado.gravacoes.push(corpo);
      if (estado.rejeitar) return route.fulfill({ status: 409, json: { message: "Balanço concluído em outra sessão." } });
      const item = estado.balanco.itens[0]; item.quantidadeFisica = corpo.quantidadeFisica; item.version++;
      return route.fulfill({ json: item });
    }
    return route.fulfill({ json: [estado.balanco] });
  });
  await page.goto("/balanco");
  await page.getByRole("button", { name: "Maximizar balanço #1", exact: true }).click();
  await expect(page.getByLabel("Contagem de Arroz")).toHaveValue("8");
  return estado;
}

test("aviso de estoque atualiza imediatamente e agrupa avisos próximos", async ({ page }) => {
  await controlarAvisos(page);
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  const antes = estado.consultas.filter(p => p === "/api/estoque").length;
  estado.saldo = 7;
  await avisar(page, ["ESTOQUE"]); await avisar(page, ["ESTOQUE"]); await avisar(page, ["CATALOGO"]);
  await expect.poll(() => estado.consultas.filter(p => p === "/api/estoque").length).toBe(antes + 1);
  await expect(page.getByRole("row").filter({ hasText: "Ingrediente do restaurante A" })).toContainText("7");
});

test("contagem remota atualiza campo livre e preserva rascunho em conflito", async ({ page }) => {
  const estado = await prepararBalanco(page);
  const campo = page.getByLabel("Contagem de Arroz");
  estado.balanco.itens[0].quantidadeFisica = 7; estado.balanco.itens[0].version++;
  await avisar(page, ["BALANCOS"]); await expect(campo).toHaveValue("7");
  await campo.fill("9");
  estado.balanco.itens[0].quantidadeFisica = 6; estado.balanco.itens[0].version++;
  await avisar(page, ["BALANCOS"]);
  await expect(page.getByText("Esta contagem mudou em outra sessão.")).toBeVisible();
  await expect(campo).toHaveValue("9");
  await expect(page.getByRole("button", { name: "OK", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Recarregar contagem" }).click();
  await expect(campo).toHaveValue("6");
});

test("conclusão remota remove edição e avisa sobre o rascunho não salvo", async ({ page }) => {
  const estado = await prepararBalanco(page);
  await page.getByLabel("Contagem de Arroz").fill("9");
  estado.balanco.status = "CONCLUIDO"; estado.balanco.version++;
  await avisar(page, ["BALANCOS"]);
  await expect(page.getByLabel("Contagem de Arroz")).toHaveCount(0);
  await expect(page.getByText("As contagens que você estava digitando não foram salvas.")).toBeVisible();
  expect(estado.gravacoes).toHaveLength(0);
});

test("gravação usa a versão consultada e sucesso depende da resposta do servidor", async ({ page }) => {
  const estado = await prepararBalanco(page);
  await page.getByLabel("Contagem de Arroz").fill("9");
  await page.getByRole("button", { name: "OK", exact: true }).click();
  await expect(page.getByText("Contagem salva", { exact: true })).toBeVisible();
  expect(estado.gravacoes).toEqual([{ quantidadeFisica: 9, version: 1 }]);
  await expect(page.getByRole("button", { name: "OK", exact: true })).toBeDisabled();
});

test("409 recupera balanço concluído mesmo quando o aviso não chegou", async ({ page }) => {
  const estado = await prepararBalanco(page);
  await page.getByLabel("Contagem de Arroz").fill("9");
  estado.rejeitar = true; estado.balanco.status = "CONCLUIDO"; estado.balanco.version++;
  await page.getByRole("button", { name: "OK", exact: true }).click();
  await expect(page.getByText("Contagem não salva", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Contagem de Arroz")).toHaveCount(0);
  await expect(page.getByText("Contagem salva", { exact: true })).toHaveCount(0);
});

test("reconexão recupera dados e troca de restaurante ignora conexão antiga", async ({ page }) => {
  await controlarAvisos(page);
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await avisar(page, [], "conectado");
  estado.saldo = 4;
  await avisar(page, [], "conectado");
  await expect(page.getByRole("row").filter({ hasText: "Ingrediente do restaurante A" })).toContainText("4");
  await entrarNaLojaB(page);
  await expect(page.getByText("Ingrediente do restaurante B", { exact: true })).toBeVisible();
  const antes = estado.consultas.filter(p => p === "/api/estoque").length;
  const fechada = await page.evaluate(() => {
    const antiga = (window as any).fontesTeste[0]; antiga.emitir("alteracao", ["ESTOQUE"]); return antiga.fechada;
  });
  expect(fechada).toBe(true);
  await page.waitForTimeout(400);
  expect(estado.consultas.filter(p => p === "/api/estoque").length).toBe(antes);
});

test("aba oculta fecha o canal e conexão perdida permite reconectar", async ({ page }) => {
  await controlarAvisos(page); await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "hidden" });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  expect(await page.evaluate(() => (window as any).fontesTeste.at(-1).fechada)).toBe(true);
  const antes = await page.evaluate(() => (window as any).fontesTeste.length);
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "visible" });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect.poll(() => page.evaluate(() => (window as any).fontesTeste.length)).toBe(antes + 1);
  await page.evaluate(() => (window as any).fontesTeste.at(-1).onerror());
  await expect.poll(() => page.evaluate(() => (window as any).fontesTeste.length)).toBe(antes + 2);
});

test("avisos durante consulta lenta aguardam uma única atualização posterior", async ({ page }) => {
  await controlarAvisos(page); const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  const consultas = () => estado.consultas.filter(p => p === "/api/estoque").length;
  const antes = consultas();
  await avisar(page, [], "conectado");
  await page.waitForTimeout(400);
  expect(consultas()).toBe(antes);
  estado.atrasarEstoqueA = true;
  await page.getByRole("button", { name: "Atualizar", exact: true }).click();
  await expect.poll(() => estado.pendentes.length).toBe(1);
  for (let i = 0; i < 4; i++) { await avisar(page, ["ESTOQUE"]); await page.waitForTimeout(300); }
  expect(consultas()).toBe(antes + 1);
  estado.atrasarEstoqueA = false; estado.saldo = 8;
  await estado.pendentes[0]();
  await expect.poll(consultas).toBe(antes + 2);
  await expect(page.getByRole("row").filter({ hasText: "Ingrediente do restaurante A" })).toContainText("8");
});
