import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";
import type { ItemNotaFiscalDTO, NotaFiscalDTO } from "../src/lib/types";

async function prepararNota(page: Page, campos: Partial<ItemNotaFiscalDTO> = {}) {
  const estado = await preparar(page);
  const nota: NotaFiscalDTO = {
    id: 1, version: 2, chave: "1".repeat(44), fornecedor: "Fornecedor de teste", documentoFornecedor: "123",
    numero: "1", serie: "1", emissao: "2026-10-19", valorTotal: 10, confirmada: false, avisos: [],
    itens: [{ id: 1, numero: 1, codigo: "BE1", descricao: "beb gaise h2o limao", unidadeNota: "UN",
      quantidadeNota: 2, valorBruto: 10, desconto: 0, valorRateado: 0, valorEntrada: 10,
      produtoId: null, categoriaId: 125, nomeProduto: "beb gaise h2o limao", unidadeEstoque: "UN",
      fator: 1, quantidadeEntrada: null, validade: null, selecionado: true, embalagemConfirmada: true,
      incerto: false, movimentacaoId: null, origemCategoria: "DESCRICAO", revisarCategoria: false,
      motivoCategoria: "Tipo reconhecido na descrição: Bebidas. Confira a sugestão antes de confirmar.", ...campos }],
  };
  await page.route("**/api/notas-fiscais/1", route => route.fulfill({ json: nota }));
  await page.goto("/notas-fiscais?id=1");
  return estado;
}

test("bebida mostra motivo da sugestão e mantém a conferência final", async ({ page }) => {
  const estado = await prepararNota(page);
  await expect(page.getByText("Tipo reconhecido na descrição: Bebidas.", { exact: false })).toBeVisible();
  await expect(page.locator("#nota-categoria-1")).toHaveValue("125");
  await page.locator("#nota-lote-1").fill(" FAB-123 ");
  await page.getByLabel("Conferi os itens, os descontos e os custos rateados").check();
  await page.getByRole("button", { name: "Confirmar entrada no estoque" }).click();
  await expect(page.getByText(/Esta nota já foi importada/)).toBeVisible();
  expect(estado.confirmacoes[0].version).toBe(2);
  expect(estado.confirmacoes[0].itens[0].categoriaId).toBe(125);
  expect(estado.confirmacoes[0].itens[0].fator).toBe(1);
  expect(estado.confirmacoes[0].itens[0].loteFabricante).toBe("FAB-123");
});

test("trinta garrafas de 500 ml mantêm trinta unidades na entrada", async ({ page }) => {
  const estado = await prepararNota(page, { descricao: "H2O 500ML", nomeProduto: "H2O 500ML", quantidadeNota: 30,
    unidadeEstoque: "ML", fator: 500 }); // Rascunho gerado antes da correção.
  await expect(page.locator("#nota-unidade-1")).toHaveValue("UN");
  await expect(page.locator("#nota-unidade-1")).toBeDisabled();
  await expect(page.getByLabel("Unidades por item da nota", { exact: true })).toHaveValue("1");
  await expect(page.getByText("Entrada: 30 UN", { exact: true })).toBeVisible();
  await page.getByLabel("Conferi os itens, os descontos e os custos rateados").check();
  await page.getByRole("button", { name: "Confirmar entrada no estoque" }).click();
  await expect(page.getByText(/Esta nota já foi importada/)).toBeVisible();
  expect(estado.confirmacoes[0].itens[0]).toMatchObject({ unidadeEstoque: "UN", quantidadeNota: 30, fator: 1 });
});

test("categoria Bebidas troca volume por unidades e permite doze garrafas por caixa", async ({ page }) => {
  const estado = await prepararNota(page, { descricao: "Marca especial 500ML", nomeProduto: "Marca especial 500ML",
    categoriaId: null, unidadeEstoque: "ML", fator: 500, unidadeNota: "CX", quantidadeNota: 2 });
  // O nome da categoria vem do catálogo real carregado pela página.
  await page.route("**/api/categorias?**", route => route.fulfill({ json: {
    content: [{ id: 125, nome: "Bebidas", ativo: true, version: 0 }], totalElements: 1, totalPages: 1, number: 0, size: 30,
  } }));
  await page.reload();
  await page.locator("#nota-categoria-1").selectOption("125");
  await expect(page.locator("#nota-unidade-1")).toHaveValue("UN");
  await expect(page.getByLabel("Unidades por item da nota", { exact: true })).toHaveValue("1");
  await page.getByLabel("Unidades por item da nota", { exact: true }).fill("12");
  await page.getByLabel("Unidades por item da nota", { exact: true }).blur();
  await expect(page.getByText("Entrada: 24 UN", { exact: true })).toBeVisible();
  await page.getByLabel("Conferi os itens, os descontos e os custos rateados").check();
  await page.getByRole("button", { name: "Confirmar entrada no estoque" }).click();
  await expect(page.getByText(/Esta nota já foi importada/)).toBeVisible();
  expect(estado.confirmacoes[0].itens[0]).toMatchObject({ unidadeEstoque: "UN", fator: 12 });
});

test("aviso de categoria permite confirmar item desconhecido sem escolha obrigatória", async ({ page }) => {
  const estado = await prepararNota(page, { categoriaId: null, origemCategoria: "NAO_IDENTIFICADA",
    revisarCategoria: true, motivoCategoria: "O nome tem mais de um sentido. Confira o produto." });
  await expect(page.getByText("Conferir categoria", { exact: true })).toBeVisible();
  await page.getByLabel("Conferi os itens, os descontos e os custos rateados").check();
  await page.getByRole("button", { name: "Confirmar entrada no estoque" }).click();
  await expect(page.getByText(/Esta nota já foi importada/)).toBeVisible();
  expect(estado.confirmacoes[0].itens[0].categoriaId).toBeNull();
});

test("editar nome limpa sugestão antiga e categoria manual substitui o aviso", async ({ page }) => {
  await prepararNota(page);
  await page.locator("#nota-nome-1").fill("LEITE DE COCO");
  await expect(page.getByText("Tipo reconhecido na descrição: Bebidas.", { exact: false })).toHaveCount(0);
  await expect(page.locator("#nota-categoria-1")).toHaveValue("");
  await expect(page.getByText("Conferir categoria", { exact: true })).toBeVisible();
  await page.locator("#nota-categoria-1").selectOption("2");
  await expect(page.getByText("Conferir categoria", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Categoria escolhida na revisão da nota.", { exact: true })).toBeVisible();
  await page.locator("#nota-categoria-geral").selectOption("3");
  await expect(page.locator("#nota-categoria-1")).toHaveValue("3");
  await expect(page.getByText("Descrição da nota", { exact: false })).toHaveCount(0);
});

test("produto existente preserva categoria e aponta para o catálogo no celular", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await prepararNota(page, { produtoId: 1, nomeProduto: "Produto 001", categoriaId: 1, unidadeEstoque: "KG",
    origemCategoria: "NOME_EXATO", revisarCategoria: true,
    motivoCategoria: "A descrição sugere outra categoria. Confira o cadastro do produto; sua categoria foi preservada." });
  await expect(page.getByText("Conferir categoria", { exact: true })).toBeVisible();
  await expect(page.getByText("Categoria do catálogo:", { exact: false })).toContainText("Categoria 001");
  await expect(page.getByRole("link", { name: "Abrir catálogo" })).toHaveAttribute("href", "/catalogo");
  await expect(page.locator("#nota-categoria-1")).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: "/tmp/estoq-categoria-mobile.png", fullPage: true });
});
