import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

const stocks = [
  { produtoId: 1, produtoNome: "Arroz", categoriaNome: "Grãos", unidadeMedida: "KG", saldoAtual: 10, saldoDisponivelConsumo: 10, valorEstoque: 100 },
  { produtoId: 2, produtoNome: "Ovos", categoriaNome: "Proteínas", unidadeMedida: "UN", saldoAtual: 20, saldoDisponivelConsumo: 20, valorEstoque: 50 },
];
const movimento = (id: number, qtd: number) => ({ id, tipo: "CONSUMO", produtoId: 1, produtoNome: "Arroz", unidadeMedida: "KG",
  quantidade: qtd, dataHora: new Date().toISOString(), loteCodigo: "LOTE", custoConsumo: 10 });

async function abrir(page: Page) {
  await preparar(page, "COZINHA");
  await page.addInitScript(() => {
    (window as any).EventSource = class extends EventTarget { close() {} };
    window.print = () => {};
  });
  const estado = { pedidos: [] as any[], atrasar: false, falhar: false, recarga: false,
    pendentes: [] as (() => Promise<void>)[], registros: [] as any[] };
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/estoque") {
      if (estado.recarga) return new Promise<void>(resolve => estado.pendentes.push(async () => { await route.fulfill({ json: stocks }); resolve(); }));
      return route.fulfill({ json: stocks });
    }
    if (path === "/api/movimentacoes") return route.fulfill({ json: estado.registros });
    if (path === "/api/consumos/lote") {
      estado.pedidos.push(route.request().postDataJSON());
      if (estado.falhar) return route.abort("connectionfailed");
      if (estado.atrasar) return new Promise<void>(resolve => estado.pendentes.push(async () => {
        await route.fulfill({ json: { estoque: stocks, movimentacoes: [movimento(1, 0.1)] } }); resolve();
      }));
      estado.registros = [movimento(1, 0.1)];
      return route.fulfill({ json: { estoque: stocks, movimentacoes: estado.registros } });
    }
    return route.fallback();
  });
  await page.goto("/consumo");
  await expect(page.getByRole("button", { name: "Quantidade de Arroz", exact: true })).toBeVisible();
  return estado;
}

test("categorias e atalhos selecionam vários itens sem digitar", async ({ page }) => {
  const estado = await abrir(page);
  await page.getByRole("button", { name: "Aumentar Arroz", exact: true }).click();
  await page.getByRole("button", { name: "Proteínas", exact: true }).click();
  await page.getByRole("button", { name: "Aumentar Ovos", exact: true }).click();
  await page.getByRole("button", { name: "Todas", exact: true }).click();
  await expect(page.getByRole("button", { name: "Quantidade de Arroz", exact: true })).toHaveText("0,1 kg");
  await page.getByRole("button", { name: "Registrar consumo de 2 itens", exact: true }).click();
  const modal = page.getByRole("dialog", { name: "Conferir itens do consumo" });
  await expect(modal.getByText("Arroz", { exact: true })).toBeVisible();
  await expect(modal.getByText("Ovos", { exact: true })).toBeVisible();
  expect(estado.pedidos).toHaveLength(0);
  await modal.getByRole("button", { name: "Confirmar consumo", exact: true }).click();
  await expect(modal).toHaveCount(0);
  expect(estado.pedidos).toHaveLength(1);
  expect(estado.pedidos[0].itens.map((item: any) => [item.produtoId, item.quantidade])).toEqual([[1, 0.1], [2, 1]]);
});

test("busca só habilita teclado após dois toques e volta a bloquear ao sair", async ({ page }) => {
  await abrir(page);
  const busca = page.getByLabel("Buscar produto", { exact: true });
  await busca.click();
  await expect(busca).toHaveAttribute("readonly", "");
  await busca.dblclick();
  await expect(busca).not.toHaveAttribute("readonly");
  await busca.fill("ovos");
  await expect(page.getByRole("button", { name: "Quantidade de Arroz", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Todas", exact: true }).click();
  await expect(busca).toHaveAttribute("readonly", "");
});

test("painel numérico aceita três casas decimais e não abre input de quantidade", async ({ page }) => {
  await abrir(page);
  await page.getByRole("button", { name: "Quantidade de Arroz", exact: true }).click();
  const modal = page.getByRole("dialog", { name: "Quantidade de Arroz" });
  await modal.getByRole("button", { name: "Limpar", exact: true }).click();
  for (const nome of ["0", "Vírgula decimal", "1", "2", "5"]) await modal.getByRole("button", { name: nome, exact: true }).click();
  await expect(modal.locator("input")).toHaveCount(0);
  await modal.getByRole("button", { name: "Aplicar quantidade", exact: true }).click();
  await expect(page.getByRole("button", { name: "Quantidade de Arroz", exact: true })).toHaveText("0,125 kg");
  await page.getByRole("button", { name: "Diminuir Arroz", exact: true }).click();
  await expect(page.getByRole("button", { name: "Quantidade de Arroz", exact: true })).toHaveText("0,025 kg");
});

test("imprimir conferência não grava nem perde seleção", async ({ page }) => {
  const estado = await abrir(page);
  await page.evaluate(() => {
    (window as any).impressoes = 0;
    const abrirJanela = window.open.bind(window);
    window.open = (...args: Parameters<typeof window.open>) => {
      const janela = abrirJanela(...args);
      if (janela) janela.print = () => { (window as any).impressoes += 1; };
      return janela;
    };
  });
  await page.getByRole("button", { name: "Aumentar Arroz", exact: true }).click();
  await page.getByRole("button", { name: "Registrar consumo de 1 item", exact: true }).click();
  const popupPromise = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Imprimir lista", exact: true }).click();
  const popup = await popupPromise;
  await expect(popup.locator("h1")).toHaveText("Lista para conferência — consumo ainda não registrado");
  await expect(popup.locator("tbody")).toContainText("Arroz");
  await expect.poll(() => page.evaluate(() => (window as any).impressoes), { timeout: 10000 }).toBe(1);
  expect(estado.pedidos).toHaveLength(0);
  await popup.close();
  await page.getByRole("button", { name: "Voltar para editar", exact: true }).click();
  await expect(page.getByRole("button", { name: "Quantidade de Arroz", exact: true })).toHaveText("0,1 kg");
});

test("impressão diária consolida consumos de diferentes lotes e embalagens", async ({ page }) => {
  const estado = await abrir(page);
  estado.registros = [movimento(1, 0.125), { ...movimento(2, 0.375), loteCodigo: "OUTRO", produtoAbertoId: 7 }];
  await page.getByRole("button", { name: "Atualizar", exact: true }).click();
  await expect(page.getByText("OUTRO", { exact: true })).toBeVisible();
  const popupPromise = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Imprimir lista do dia", exact: true }).click();
  const popup = await popupPromise;
  await expect(popup.locator("tbody tr")).toHaveCount(1);
  await expect(popup.locator("tbody")).toContainText("0,5");
  await expect(popup.locator("tbody")).toContainText("kg");
  await expect(popup.locator("body")).not.toContainText("R$");
});

test("falha de conexão conserva a chave e o pedido para reenvio", async ({ page }) => {
  const estado = await abrir(page); estado.falhar = true;
  await page.getByRole("button", { name: "Aumentar Arroz", exact: true }).click();
  await page.getByRole("button", { name: "Registrar consumo de 1 item", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar consumo", exact: true }).click();
  await expect(page.getByRole("button", { name: "Tentar novamente", exact: true })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Voltar para editar", exact: true })).toBeDisabled();
  estado.falhar = false;
  await page.getByRole("button", { name: "Tentar novamente", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(estado.pedidos).toHaveLength(2);
  expect(estado.pedidos[0]).toEqual(estado.pedidos[1]);
});

test("confirmação não espera recarga e cliques repetidos não enviam duas vezes", async ({ page }) => {
  const estado = await abrir(page); estado.atrasar = true;
  await page.getByRole("button", { name: "Aumentar Arroz", exact: true }).click();
  await page.getByRole("button", { name: "Registrar consumo de 1 item", exact: true }).click();
  const confirmar = page.getByRole("button", { name: "Confirmar consumo", exact: true });
  await confirmar.dblclick();
  await expect(confirmar).toBeDisabled();
  await expect.poll(() => estado.pedidos.length).toBe(1);
  estado.recarga = true;
  await estado.pendentes.shift()!();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByText("Consumo registrado", { exact: true })).toBeVisible();
  expect(estado.pedidos).toHaveLength(1);
  estado.recarga = false;
  for (const liberar of estado.pendentes.splice(0)) await liberar();
});

for (const largura of [320, 768, 1440]) {
  test(`controles e conferência cabem em ${largura}px`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: 900 });
    await abrir(page);
    await page.getByRole("button", { name: "Aumentar Arroz", exact: true }).click();
    await page.getByRole("button", { name: "Registrar consumo de 1 item", exact: true }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await expect(page.getByRole("button", { name: "Imprimir lista", exact: true })).toBeVisible();
  });
}

test("lista longa mantém a confirmação acessível sem ir ao último produto", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await abrir(page);
  await page.route("**/api/estoque", route => route.fulfill({ json: Array.from({ length: 120 }, (_, i) => ({
    ...stocks[0], produtoId: i + 1, produtoNome: `Ingrediente ${i + 1}`,
  })) }));
  await page.reload();
  await page.getByRole("button", { name: "Aumentar Ingrediente 1", exact: true }).click();
  await expect(page.getByRole("button", { name: "Registrar consumo de 1 item", exact: true })).toBeInViewport();
});

test("impressão trata nomes como texto e conserva linhas entre páginas", async ({ page }) => {
  const estado = await abrir(page);
  estado.registros = Array.from({ length: 80 }, (_, i) => ({ ...movimento(i + 1, 1), produtoId: i + 1,
    produtoNome: i === 0 ? '<img src=x onerror="window.injetado=true">' : `Produto com nome longo para impressão ${i + 1}` }));
  await page.getByRole("button", { name: "Atualizar", exact: true }).click();
  await expect(page.getByText("Produto com nome longo para impressão 80", { exact: true })).toBeVisible();
  const popupPromise = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Imprimir lista do dia", exact: true }).click();
  const popup = await popupPromise;
  await expect(popup.locator("tbody tr")).toHaveCount(80);
  await expect(popup.locator("tbody img")).toHaveCount(0);
  await expect(popup.locator("tbody")).toContainText('<img src=x onerror="window.injetado=true">');
  await popup.emulateMedia({ media: "print" });
  expect(await popup.locator("tbody tr").first().evaluate(el => getComputedStyle(el).breakInside)).toBe("avoid");
  expect(await popup.locator("thead").evaluate(el => getComputedStyle(el).display)).toBe("table-header-group");
});
