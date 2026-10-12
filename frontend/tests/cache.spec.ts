import { test, expect, type Page } from "@playwright/test";
import { preparar, abrirMenu } from "./fixtures";

async function navegar(page: Page, nome: string) {
  await abrirMenu(page);
  await page.getByRole("link", { name: nome, exact: true }).click();
}

test("revisitar estoque e páginas do catálogo reutiliza os dados sem novas consultas", async ({ page }) => {
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  expect(estado.consultas.filter(p => p === "/api/estoque")).toHaveLength(1);
  await navegar(page, "Catálogo");
  await expect(page.getByRole("button", { name: "Produto 001", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Próxima", exact: true }).click();
  await expect(page.getByText(/Página 2 de 3/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Produto 051", exact: true })).toBeVisible();
  const consultas = estado.consultas.filter(p => p.startsWith("/api/produtos?")).length;
  await page.getByRole("button", { name: "Anterior", exact: true }).click();
  await expect(page.getByRole("button", { name: "Produto 001", exact: true })).toBeVisible();
  expect(estado.consultas.filter(p => p.startsWith("/api/produtos?")).length).toBe(consultas);
  const busca = page.getByLabel("Buscar no catálogo", { exact: true });
  await busca.fill("Produto 125");
  await expect(page.getByRole("button", { name: "Produto 125", exact: true })).toBeVisible();
  const filtradas = () => estado.consultas.filter(p => p.startsWith("/api/produtos?") && p.includes("q=Produto")).length;
  const antesFiltro = filtradas();
  await busca.fill("");
  await expect(page.getByRole("button", { name: "Produto 001", exact: true })).toBeVisible();
  await busca.fill("Produto 125");
  await expect(page.getByRole("button", { name: "Produto 125", exact: true })).toBeVisible();
  expect(filtradas()).toBe(antesFiltro);
  await navegar(page, "Estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await expect(page.getByText("Carregando estoque…")).toHaveCount(0);
  expect(estado.consultas.filter(p => p === "/api/estoque")).toHaveLength(1);
});

test("consultas operacionais atualizam em 15 segundos e cadastros em um minuto", async ({ page }) => {
  await page.clock.install();
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await page.clock.fastForward(15_100);
  await expect.poll(() => estado.consultas.filter(p => p === "/api/estoque").length).toBe(2);
  await navegar(page, "Catálogo");
  await expect(page.getByRole("button", { name: "Produto 001", exact: true })).toBeVisible();
  const consultas = () => estado.consultas.filter(p => p.startsWith("/api/produtos?") && p.includes("size=50")).length;
  const antes = consultas();
  await page.clock.fastForward(15_100);
  expect(consultas()).toBe(antes);
  await page.clock.fastForward(45_100);
  await expect.poll(consultas).toBe(antes + 1);
});

test("falha em atualização preserva a lista e permite tentar novamente", async ({ page }) => {
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  estado.falharEstoque = true;
  await page.getByRole("button", { name: "Atualizar", exact: true }).click();
  await expect(page.getByRole("button", { name: "Tentar novamente", exact: true })).toBeVisible();
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await expect(page.getByText("Carregando estoque…")).toHaveCount(0);
  estado.falharEstoque = false;
  await page.getByRole("button", { name: "Tentar novamente", exact: true }).click();
  await expect(page.getByRole("button", { name: "Tentar novamente", exact: true })).toHaveCount(0);
  await expect(page.getByText("Servidor indisponível no teste")).toHaveCount(0);
});

test("gravação atualiza listas abertas e descarta uma leitura anterior à operação", async ({ page }) => {
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  estado.atrasarEstoqueA = true;
  await page.getByRole("button", { name: "Atualizar", exact: true }).click();
  await expect.poll(() => estado.pendentes.length).toBe(1);
  estado.atrasarEstoqueA = false;
  estado.saldo = 2;
  await page.evaluate(async () => {
    const modulo = "/src/lib/api.ts";
    const { api } = await import(modulo);
    await api.post("/api/consumos", {});
  });
  await expect.poll(() => estado.consultas.filter(p => p === "/api/estoque").length).toBe(3);
  const linha = page.getByRole("row").filter({ hasText: "Ingrediente do restaurante A" });
  await expect(linha).toContainText("2");
  for (const liberar of estado.pendentes) await liberar();
  // A lista segue com a posição nova mesmo após a resposta atrasada com saldo 1.
  await expect(linha).toContainText("2");
  const salvo = await page.evaluate(async () => {
    const modulo = "/src/lib/cache.ts";
    const { lerCache, chaveCache } = await import(modulo);
    return lerCache(chaveCache("/api/estoque")).data[0].saldoAtual;
  });
  expect(salvo).toBe(2);
});

test("cache compartilha requisições e limita retenção por quantidade e tamanho", async ({ page }) => {
  await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  const resultado = await page.evaluate(async () => {
    const modulo = "/src/lib/cache.ts";
    const { buscarCache, gravarCache, lerCache, chaveCache } = await import(modulo);
    const chave = chaveCache("/teste/compartilhado");
    let chamadas = 0;
    const carregar = async () => { chamadas++; return ["compartilhado"]; };
    await Promise.all([buscarCache(chave, carregar), buscarCache(chave, carregar)]);
    for (let i = 0; i < 201; i++) gravarCache(chaveCache(`/teste/${i}`), i);
    const antiga = !!lerCache(chaveCache("/teste/0"));
    const recente = lerCache(chaveCache("/teste/200"))?.data;
    for (let i = 0; i < 3; i++) gravarCache(chaveCache(`/teste/bytes/${i}`), "x".repeat(8 * 1024 * 1024));
    const limiteBytes = !lerCache(chaveCache("/teste/bytes/0")) && !!lerCache(chaveCache("/teste/bytes/2"));
    gravarCache(chaveCache("/teste/grande"), "x".repeat(21 * 1024 * 1024));
    return { chamadas, antiga, recente, limiteBytes, grande: !!lerCache(chaveCache("/teste/grande")) };
  });
  expect(resultado).toEqual({ chamadas: 1, antiga: false, recente: 200, limiteBytes: true, grande: false });
});

test("sem conexão pausa consultas e atualiza quando a conexão retorna", async ({ page }) => {
  await page.clock.install();
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await page.evaluate(() => Object.defineProperty(navigator, "onLine", { configurable: true, get: () => false }));
  await page.clock.fastForward(30_100);
  expect(estado.consultas.filter(p => p === "/api/estoque")).toHaveLength(1);
  await page.evaluate(() => {
    Object.defineProperty(navigator, "onLine", { configurable: true, get: () => true });
    window.dispatchEvent(new Event("online"));
  });
  await expect.poll(() => estado.consultas.filter(p => p === "/api/estoque").length).toBe(2);
});

test("aba oculta pausa consultas e atualiza ao recuperar visibilidade", async ({ page }) => {
  await page.clock.install();
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await page.evaluate(() => Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "hidden" }));
  await page.clock.fastForward(30_100);
  expect(estado.consultas.filter(p => p === "/api/estoque")).toHaveLength(1);
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "visible" });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect.poll(() => estado.consultas.filter(p => p === "/api/estoque").length).toBe(2);
});
