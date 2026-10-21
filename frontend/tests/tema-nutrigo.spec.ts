import { test, expect } from "@playwright/test";
import { preparar, abrirMenu } from "./fixtures";

function contraste(frente: string, fundo: string) {
  function luminancia(cor: string) {
    const canais = cor.match(/[\d.]+/g)!.slice(0, 3).map(Number).map(v => {
      const c = v / 255;
      return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return canais[0] * 0.2126 + canais[1] * 0.7152 + canais[2] * 0.0722;
  }
  const a = luminancia(frente), b = luminancia(fundo);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

for (const largura of [320, 390, 1440]) {
  test(`tema carrega Poppins com acentos, sem cortes novos, em ${largura}px`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: 1000 });
    await preparar(page);
    await page.goto("/catalogo");
    await expect(page.locator("h1")).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    expect(await page.evaluate(() => getComputedStyle(document.body).fontFamily)).toContain("Poppins");
    for (const peso of [400, 500, 600]) {
      expect(await page.evaluate(p => document.fonts.check(`${p} 15px Poppins`, "Ação, café, pão, açúcar, informações"), peso)).toBe(true);
    }
    await expect(page.locator("h1")).toHaveCSS("font-weight", "600");
    await expect(page.getByRole("button", { name: "Novo produto", exact: true })).toHaveCSS("font-weight", "500");
    await abrirMenu(page);
    const ativo = page.locator(".navitem--active");
    await expect(ativo).toHaveCSS("background-color", "rgb(194, 230, 110)");
    await expect(ativo).toHaveCSS("color", "rgb(39, 41, 50)");
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  });
}

test("cores de texto, botões e estados mantêm contraste AA", async ({ page }) => {
  await preparar(page);
  await page.goto("/catalogo");
  await expect(page.locator("h1")).toBeVisible();
  const amostras = await page.evaluate(() => {
    const estilos = ["btn btn--accent", "btn btn--ink", "btn btn--danger", "btn btn--good",
      "badge badge--neutral", "badge badge--accent", "badge badge--good", "badge badge--warn",
      "badge badge--bad", "badge badge--info", "alertbanner alertbanner--warn"];
    const container = document.createElement("div");
    document.body.append(container);
    const resultado = estilos.map(classe => {
      const elemento = document.createElement("button");
      elemento.className = classe;
      elemento.textContent = "Informações de teste";
      container.append(elemento);
      const s = getComputedStyle(elemento);
      return { classe, frente: s.color, fundo: s.backgroundColor };
    });
    const texto = document.createElement("p");
    texto.className = "muted";
    container.style.background = "var(--paper)";
    container.append(texto);
    resultado.push({ classe: "muted sobre paper", frente: getComputedStyle(texto).color,
      fundo: getComputedStyle(container).backgroundColor });
    container.remove();
    return resultado;
  });
  for (const a of amostras) expect(contraste(a.frente, a.fundo), a.classe).toBeGreaterThanOrEqual(4.5);
  const status = amostras.filter(a => ["badge badge--good", "badge badge--warn", "badge badge--bad"].includes(a.classe));
  expect(new Set(status.map(a => a.fundo)).size).toBe(3);
});

test("hover, foco e seleção usam os tokens sem depender do laranja antigo", async ({ page }) => {
  await preparar(page);
  await page.goto("/catalogo");
  const botao = page.getByRole("button", { name: "Novo produto", exact: true });
  await botao.hover();
  await expect(botao).toHaveCSS("background-color", "rgb(223, 249, 162)");
  await botao.evaluate(e => { (e as HTMLButtonElement).disabled = true; });
  await botao.hover({ force: true });
  await expect(botao).toHaveCSS("background-color", "rgb(238, 238, 239)");
  await expect(botao).toHaveCSS("color", "rgb(39, 41, 50)");
  await page.locator("input").first().focus();
  await expect(page.locator("input").first()).toHaveCSS("border-top-color", "rgb(82, 84, 91)");
  const selecao = await page.locator("h1").evaluate(e => {
    const s = getComputedStyle(e, "::selection");
    return { frente: s.color, fundo: s.backgroundColor };
  });
  expect(contraste(selecao.frente, selecao.fundo)).toBeGreaterThanOrEqual(4.5);
});
