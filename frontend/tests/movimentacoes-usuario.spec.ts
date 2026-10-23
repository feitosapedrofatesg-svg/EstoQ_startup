import { test, expect, type Page } from "@playwright/test";
import { preparar } from "./fixtures";

async function prepararHistorico(page: Page, path = "/movimentacoes", falharUsuarios = false) {
  await preparar(page);
  await page.clock.install({ time: new Date("2026-10-23T15:00:00Z") });
  const estado = { falharUsuarios, consultas: [] as URL[], paginasUsuarios: [] as number[],
    atrasarUsuario: "", pendentes: [] as (() => Promise<void>)[] };
  const usuarios = Array.from({ length: 126 }, (_, i) => ({ id: i + 1, version: 0,
    nome: `Pessoa ${i + 1}`, email: `pessoa${i + 1}@teste.local`, perfil: "COZINHA", ativo: true,
    dataHoraCriacao: "2026-10-01T10:00:00" }));
  Object.assign(usuarios[0], { nome: "Administrador", perfil: "ADMIN" });
  Object.assign(usuarios[1], { nome: "Ana", email: "ana.ativa@teste.local" });
  Object.assign(usuarios[124], { nome: "Ana", email: "ana.inativa@teste.local", ativo: false });
  Object.assign(usuarios[125], { nome: "Sem registros" });
  const movimentos = [
    { id: 1, tipo: "ENTRADA", usuarioId: 1, usuarioNome: "Administrador", produtoId: 1, valorTotalPago: 100 },
    { id: 2, tipo: "CONSUMO", usuarioId: 2, usuarioNome: "Ana", produtoId: 1, custoConsumo: 10 },
    { id: 3, tipo: "DESPERDICIO", usuarioId: 125, usuarioNome: "Ana", produtoId: 1, valorPrejuizo: 5 },
    { id: 4, tipo: "CONSUMO", usuarioId: 2, usuarioNome: "Ana", produtoId: 2, custoConsumo: 20 },
    { id: 5, tipo: "CONSUMO", usuarioId: 125, usuarioNome: "Ana", produtoId: 1, custoConsumo: 8 },
  ].map(m => ({ dataHora: "2026-10-23T12:00:00", produtoNome: `Ingrediente ${m.produtoId}`,
    loteId: 1, loteCodigo: "LOTE-TESTE", quantidade: 1, unidadeMedida: "UN", observacao: `Registro ${m.id}`, ...m }));
  await page.route("**/api/usuarios**", route => {
    if (estado.falharUsuarios) return route.fulfill({ status: 503, json: { message: "Falha de teste" } });
    const url = new URL(route.request().url());
    const number = Number(url.searchParams.get("page") ?? 0), size = Number(url.searchParams.get("size") ?? 100);
    estado.paginasUsuarios.push(number);
    return route.fulfill({ json: { content: usuarios.slice(number * size, (number + 1) * size), number, size,
      totalElements: usuarios.length, totalPages: Math.ceil(usuarios.length / size) } });
  });
  await page.route("**/api/movimentacoes**", async route => {
    const url = new URL(route.request().url()); estado.consultas.push(url);
    const dados = movimentos.filter(m =>
      (!url.searchParams.has("usuarioId") || String(m.usuarioId) === url.searchParams.get("usuarioId")) &&
      (!url.searchParams.has("produtoId") || String(m.produtoId) === url.searchParams.get("produtoId")) &&
      (!url.searchParams.has("tipo") || m.tipo === url.searchParams.get("tipo")) &&
      m.dataHora.slice(0, 10) >= (url.searchParams.get("dataInicio") ?? "1970-01-01") &&
      m.dataHora.slice(0, 10) <= (url.searchParams.get("dataFim") || "2999-01-01"));
    if (estado.atrasarUsuario && url.searchParams.get("usuarioId") === estado.atrasarUsuario) {
      return new Promise<void>(resolve => estado.pendentes.push(async () => { await route.fulfill({ json: dados }); resolve(); }));
    }
    return route.fulfill({ json: dados });
  });
  await page.goto(path);
  return estado;
}

async function selecionarUsuario(page: Page, email?: string) {
  const filtro = page.locator(".movimentacoes-usuario");
  await filtro.locator(".searchselect__trigger").click();
  if (email) {
    await page.getByRole("textbox", { name: "Buscar Usuário", exact: true }).fill(email);
    await filtro.getByRole("option").filter({ hasText: email }).click();
  } else await filtro.getByRole("option", { name: "Todos os usuários", exact: true }).click();
}

for (const width of [320, 1440]) {
  test(`filtro por usuário combina produto, tipo e datas, incluindo inativos em ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    const estado = await prepararHistorico(page, "/movimentacoes?produto=1");
    await selecionarUsuario(page, "ana.ativa@teste.local");
    await expect(page.locator("tbody tr")).toHaveCount(1);
    await expect(page.locator('td[data-label="Usuário"]')).toHaveText("Ana");
    await expect(page.locator(".mini-stat").nth(1)).toContainText(/10,00/);
    await page.getByRole("button", { name: "Consumos", exact: true }).click();
    await page.locator("#mv-inicio").fill("2026-10-22");
    await page.locator("#mv-fim").fill("2026-10-24");
    await selecionarUsuario(page, "ana.inativa@teste.local");
    await expect(page.locator("tbody tr")).toHaveCount(1);
    await expect(page.getByText("Registro 5", { exact: true })).toBeVisible();
    await expect(page.locator(".movimentacoes-usuario")).toContainText("Inativo");
    await expect(page.locator(".mini-stat").nth(1)).toContainText(/8,00/);
    await expect.poll(() => estado.consultas.at(-1)?.searchParams.get("usuarioId")).toBe("125");
    const query = estado.consultas.at(-1)!.searchParams;
    expect(Object.fromEntries(query)).toMatchObject({ usuarioId: "125", produtoId: "1", tipo: "CONSUMO",
      dataInicio: "2026-10-22", dataFim: "2026-10-24" });
    expect(estado.paginasUsuarios).toContain(1);
    await page.getByRole("button", { name: "Desperdícios", exact: true }).click();
    await expect(page.getByText("Registro 3", { exact: true })).toBeVisible();
    await expect(page.locator(".mini-stat").nth(2)).toContainText(/5,00/);
    await page.reload();
    await expect(page.locator(".movimentacoes-usuario")).toContainText("ana.inativa@teste.local");
    await expect(page.getByText("Registro 3", { exact: true })).toBeVisible();
    await selecionarUsuario(page);
    expect(new URL(page.url()).searchParams.has("usuario")).toBe(false);
    expect(new URL(page.url()).searchParams.get("produto")).toBe("1");
    expect(new URL(page.url()).searchParams.get("tipo")).toBe("DESPERDICIO");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await expect(page.locator(".dashboard-tactile-control")).toHaveCount(0);
  });
}

test("usuário inexistente não volta silenciosamente para Todos", async ({ page }) => {
  const estado = await prepararHistorico(page, "/movimentacoes?usuario=999");
  await expect(page.locator(".movimentacoes-usuario")).toContainText("Usuário #999");
  await expect(page.getByText("Nenhuma movimentação para este usuário no período", { exact: true })).toBeVisible();
  expect(estado.consultas.at(-1)?.searchParams.get("usuarioId")).toBe("999");
});

test("falha ao carregar equipe preserva o filtro e permite tentar novamente", async ({ page }) => {
  const estado = await prepararHistorico(page, "/movimentacoes?usuario=125", true);
  await expect(page.getByRole("alert")).toContainText("Não foi possível carregar os usuários");
  await expect(page.locator("tbody tr")).toHaveCount(2);
  await expect(page.locator('td[data-label="Usuário"]').first()).toHaveText("Ana");
  expect(new URL(page.url()).searchParams.get("usuario")).toBe("125");
  estado.falharUsuarios = false;
  await page.getByRole("button", { name: "Tentar carregar usuários", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(page.locator(".movimentacoes-usuario")).toContainText("ana.inativa@teste.local");
});

test("resposta atrasada do usuário anterior não substitui o filtro atual", async ({ page }) => {
  const estado = await prepararHistorico(page);
  estado.atrasarUsuario = "2";
  await selecionarUsuario(page, "ana.ativa@teste.local");
  await expect.poll(() => estado.pendentes.length).toBeGreaterThan(0);
  await selecionarUsuario(page, "ana.inativa@teste.local");
  await expect(page.locator("tbody tr")).toHaveCount(2);
  await expect(page.getByText("Registro 3", { exact: true })).toBeVisible();
  for (const liberar of estado.pendentes) await liberar();
  await expect(page.getByText("Registro 2", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Registro 3", { exact: true })).toBeVisible();
  expect(new URL(page.url()).searchParams.get("usuario")).toBe("125");
});

for (const perfil of ["COZINHA", "NUTRICIONISTA"]) {
  test(`${perfil} continua sem acesso à tela e não consulta a equipe`, async ({ page }) => {
    const estado = await preparar(page, perfil);
    await page.goto("/movimentacoes?usuario=1");
    await expect(page.getByText("Esta área não está disponível para o seu perfil", { exact: true })).toBeVisible();
    expect(estado.consultas.some(path => path.startsWith("/api/usuarios"))).toBe(false);
  });
}
