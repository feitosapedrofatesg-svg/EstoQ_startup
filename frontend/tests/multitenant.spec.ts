import { test, expect, type Page } from "@playwright/test";

/** Dados fictícios: os testes de interface nunca consultam a produção. */
async function preparar(page: Page, perfil = "ADMIN") {
  let usuario: any = { id: 1, nome: "Responsável A", email: "a@teste.com", perfil,
    restauranteId: perfil === "PLATAFORMA" ? null : 1, restauranteNome: "Restaurante A" };
  const categorias = Array.from({ length: 125 }, (_, i) => ({ id: i + 1, version: 0, ativo: true, nome: `Categoria ${String(i + 1).padStart(3, "0")}` }));
  const produtos = categorias.map(c => ({ id: c.id, version: 0, ativo: true, nome: `Produto ${String(c.id).padStart(3, "0")}`,
    categoriaId: c.id, categoriaNome: c.nome, unidadeMedida: "KG", codigoBarras: `COD${c.id}`, saldoAtual: 0 }));
  const usuarios = categorias.map(c => ({ id: c.id, version: 0, nome: `Pessoa ${String(c.id).padStart(3, "0")}`,
    email: `pessoa${c.id}@teste.com`, perfil: "COZINHA", ativo: true, dataHoraCriacao: "2026-01-01T12:00:00" }));
  const nota = { id: 1, version: 0, chave: "1".repeat(44), fornecedor: "Fornecedor de teste", documentoFornecedor: "123",
    numero: "1", serie: "1", emissao: "2026-01-01", valorTotal: 10, confirmada: false, avisos: [], itens: [{
      id: 1, numero: 1, codigo: "AR1", descricao: "ARROZ", unidadeNota: "KG", quantidadeNota: 1, valorBruto: 10,
      desconto: 0, valorRateado: 0, valorEntrada: 10, produtoId: null, categoriaId: null, nomeProduto: "ARROZ",
      unidadeEstoque: "KG", fator: 1, quantidadeEntrada: 1, validade: null, selecionado: true,
      embalagemConfirmada: false, incerto: true, movimentacaoId: null }] };
  const estado = { atrasarEstoqueA: false, expirarSessao: false, respostaAntiga401: false, pendentes: [] as (() => Promise<void>)[], confirmacoes: [] as any[] };
  function paginar(lista: any[], url: URL) {
    const q = (url.searchParams.get("q") ?? "").toLowerCase();
    const filtrada = lista.filter(x => `${x.nome} ${x.email ?? ""} ${x.categoriaNome ?? ""} ${x.codigoBarras ?? ""}`.toLowerCase().includes(q));
    const size = Math.min(100, Number(url.searchParams.get("size") ?? 100));
    const number = Number(url.searchParams.get("page") ?? 0);
    return { content: filtrada.slice(number * size, (number + 1) * size), number, size,
      totalPages: Math.ceil(filtrada.length / size), totalElements: filtrada.length, empty: filtrada.length === 0 };
  }
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const json = (body: any) => route.fulfill({ json: body });
    if (path === "/api/auth/csrf") return json({ token: "teste", headerName: "X-CSRF-TOKEN" });
    if (path === "/api/auth/me") return usuario ? json(usuario) : route.fulfill({ status: 401, json: { message: "Sessão expirada" } });
    if (path === "/api/auth/logout") { usuario = null; return route.fulfill({ status: 204 }); }
    if (path === "/api/auth/login") {
      usuario = { id: 2, nome: "Responsável B", email: "b@teste.com", perfil: "ADMIN", restauranteId: 2, restauranteNome: "Restaurante B" };
      return json(usuario);
    }
    if (path === "/api/produtos") return json(paginar(produtos, url));
    if (path === "/api/categorias") return json(paginar(categorias, url));
    if (path === "/api/usuarios") return json(paginar(usuarios, url));
    if (path.startsWith("/api/parametros-estoque/por-produto/")) return json({ id: 1000, version: 0, produtoId: Number(path.split("/").at(-1)), diasAlertaVencimento: 7 });
    if (path === "/api/estoque") {
      if (estado.expirarSessao) { usuario = null; return route.fulfill({ status: 401, json: { message: "Sessão expirada" } }); }
      const restaurante = usuario?.restauranteId;
      const resposta = [{ produtoId: 1, produtoNome: `Ingrediente do restaurante ${restaurante === 2 ? "B" : "A"}`,
        categoriaNome: "Grãos", unidadeMedida: "KG", saldoAtual: 1, saldoDisponivel: 1, valorEstoque: 10,
        abaixoDoMinimo: false, possuiItensAbertos: false }];
      if (estado.atrasarEstoqueA && restaurante === 1) {
        return new Promise<void>(resolve => estado.pendentes.push(async () => { if (estado.respostaAntiga401) await route.fulfill({ status: 401, json: { message: "Sessão anterior expirada" } });
          else await json(resposta); resolve(); }));
      }
      return json(resposta);
    }
    if (path === "/api/notas-fiscais/1/confirmar") {
      estado.confirmacoes.push(route.request().postDataJSON());
      return json({ ...nota, confirmada: true });
    }
    if (path === "/api/notas-fiscais/1") return json(nota);
    return json([]);
  });
  return estado;
}

async function abrirMenu(page: Page) {
  const botao = page.getByRole("button", { name: "Abrir menu", exact: true });
  if (await botao.isVisible()) await botao.click();
}

async function entrarNaLojaB(page: Page) {
  await abrirMenu(page);
  await page.getByRole("button", { name: "Sair da conta" }).click();
  await expect(page).toHaveURL(/login/);
  await page.locator("#login-email").fill("b@teste.com");
  await page.locator("#login-senha").fill("Teste@12345");
  await page.getByRole("button", { name: "Entrar", exact: true }).click();
  await expect(page).not.toHaveURL(/login/);
  await abrirMenu(page);
  await page.getByRole("link", { name: "Estoque", exact: true }).click();
}

test("trocar de restaurante limpa os dados anteriormente carregados", async ({ page }) => {
  await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  await entrarNaLojaB(page);
  await expect(page.getByText("Ingrediente do restaurante B", { exact: true })).toBeVisible();
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toHaveCount(0);
});

for (const status of [200, 401]) test(`resposta atrasada ${status} da sessão anterior não contamina a nova sessão`, async ({ page }) => {
  const estado = await preparar(page);
  estado.atrasarEstoqueA = true;
  estado.respostaAntiga401 = status === 401;
  await page.goto("/estoque");
  await expect.poll(() => estado.pendentes.length).toBeGreaterThan(0);
  await entrarNaLojaB(page);
  await expect(page.getByText("Ingrediente do restaurante B", { exact: true })).toBeVisible();
  for (const liberar of estado.pendentes) await liberar();
  await abrirMenu(page);
  await page.getByRole("link", { name: "Catálogo", exact: true }).click();
  await abrirMenu(page);
  await page.getByRole("link", { name: "Estoque", exact: true }).click();
  await expect(page.getByText("Ingrediente do restaurante B", { exact: true })).toBeVisible();
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toHaveCount(0);
});

for (const perfil of ["ADMIN", "COZINHA", "NUTRICIONISTA"]) {
  test(`${perfil} não encontra a tela de backup`, async ({ page }) => {
    await preparar(page, perfil);
    await page.goto("/backup");
    await expect(page.getByText("Esta área não está disponível para o seu perfil", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: /Backup/ })).toHaveCount(0);
  });
}

test("PLATAFORMA encontra o backup completo", async ({ page }) => {
  await preparar(page, "PLATAFORMA");
  await page.goto("/backup");
  await expect(page.getByRole("heading", { name: "Backup completo da plataforma" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Backup completo" })).toBeVisible();
});

test("catálogo, categorias e usuários alcançam registros além dos primeiros cem", async ({ page }) => {
  await preparar(page);
  await page.goto("/catalogo");
  await page.getByRole("button", { name: "Próxima", exact: true }).click();
  await expect(page.getByText(/Página 2 de 3/)).toBeVisible();
  await page.getByRole("button", { name: "Próxima", exact: true }).click();
  await expect(page.getByRole("button", { name: "Produto 125", exact: true })).toBeVisible();
  await page.getByLabel("Buscar no catálogo", { exact: true }).fill("Produto 125");
  await expect(page.getByRole("button", { name: /^Produto / })).toHaveCount(1);
  await page.getByLabel("Buscar no catálogo", { exact: true }).fill("");
  await page.getByRole("tab", { name: "Categorias", exact: true }).click();
  await page.getByLabel("Buscar no catálogo", { exact: true }).fill("Categoria 125");
  await expect(page.getByText("Categoria 125", { exact: true })).toBeVisible();
  await abrirMenu(page);
  await page.getByRole("link", { name: "Usuários", exact: true }).click();
  await page.getByLabel("Buscar usuário").fill("pessoa125@teste.com");
  await expect(page.getByRole("cell", { name: "pessoa125@teste.com", exact: true })).toBeVisible();
});

test("nota carrega todas as categorias e confirma com a conferência final", async ({ page }) => {
  const estado = await preparar(page);
  await page.goto("/notas-fiscais?id=1");
  await expect(page.locator("#nota-categoria-geral option")).toHaveCount(126);
  await page.locator("#nota-categoria-geral").selectOption("125");
  await page.getByLabel("Conferi os itens, os descontos e os custos rateados").check();
  await page.getByRole("button", { name: "Confirmar entrada no estoque" }).click();
  await expect(page.getByText("Esta nota já foi importada. Enviá-la novamente não duplica o estoque.")).toBeVisible();
  expect(estado.confirmacoes).toHaveLength(1);
  expect(estado.confirmacoes[0].custosConferidos).toBe(true);
  expect(estado.confirmacoes[0].itens[0].categoriaId).toBe(125);
});


test("expiração da sessão atual remove os dados e retorna ao login", async ({ page }) => {
  const estado = await preparar(page);
  await page.goto("/estoque");
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toBeVisible();
  estado.expirarSessao = true;
  await page.getByRole("button", { name: "Atualizar", exact: true }).click();
  await expect(page).toHaveURL(/login/);
  await expect(page.getByText("Ingrediente do restaurante A", { exact: true })).toHaveCount(0);
});
