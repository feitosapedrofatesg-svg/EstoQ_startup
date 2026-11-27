import { test, expect } from "@playwright/test";
import { preparar, entrarNaLojaB } from "./fixtures";

for (const perfil of ["ADMIN","COZINHA","OPERACIONAL","NUTRICIONISTA","PLATAFORMA"]) {
  test(`configurações e notificações disponíveis para ${perfil}`,async({page})=>{
    await preparar(page,perfil);
    await page.goto(perfil==="PLATAFORMA"?"/plataforma":perfil==="NUTRICIONISTA"?"/relatorios":"/estoque");
    const botao=page.getByRole("button",{name:"Configurações",exact:true});
    await expect(botao).toBeVisible();
    if(["ADMIN","COZINHA","OPERACIONAL"].includes(perfil)){
      await expect(page.locator(".bell__btn")).toBeVisible();
      const sino=await page.locator(".bell__btn").boundingBox();const ajustes=await botao.boundingBox();
      expect(ajustes!.x).toBeGreaterThan(sino!.x);expect(Math.abs(ajustes!.y-sino!.y)).toBeLessThan(15);
    }
    await botao.click();
    const modal=page.getByRole("dialog");
    await expect(modal.getByLabel("Tema",{exact:true})).toBeVisible();
    await expect(modal.getByLabel("Idioma",{exact:true})).toBeVisible();
    await expect(modal.locator('a[href="mailto:estoq.sistema@gmail.com"]')).toBeVisible();
    await expect(modal.locator('a[href="/termos"]')).toBeVisible();
    await expect(modal.locator('a[href="/privacidade"]')).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(modal).toHaveCount(0);
    await expect(botao).toBeFocused();
  });
}

for(const largura of [375,768,1440])test(`temas, fontes e idiomas sem perda de formulário em ${largura}px`,async({page,context},testInfo)=>{
  await page.setViewportSize({width:largura,height:900});
  await preparar(page,"ADMIN");
  let preferencias={tema:"claro",fonte:100,idioma:"pt-BR"};
  await page.route("**/api/auth/preferencias",async route=>{
    if(route.request().method()==="PUT")preferencias=route.request().postDataJSON();
    await route.fulfill({json:preferencias});
  });
  await context.grantPermissions(["clipboard-read","clipboard-write"]);
  await page.goto("/estoque");
  await expect(page.locator("html")).toHaveAttribute("data-tema","claro");
  const busca=page.locator('input[type="search"]').first();
  await busca.fill("Ingrediente");
  await page.getByRole("button",{name:"Configurações",exact:true}).click();
  const modal=page.getByRole("dialog");
  for(const tema of ["escuro","original","terroso","roxo","verde","azul","claro"]){
    await modal.getByLabel("Tema",{exact:true}).selectOption(tema);
    await expect(page.locator("html")).toHaveAttribute("data-tema",tema);
  }
  for(let i=0;i<5;i++)await modal.getByRole("button",{name:"Aumentar fonte",exact:true}).click();
  await expect(page.locator("html")).toHaveCSS("font-size","20px");
  await expect(modal.getByRole("button",{name:"Aumentar fonte",exact:true})).toBeDisabled();
  await modal.getByLabel("Idioma",{exact:true}).selectOption("en");
  await expect(modal.getByRole("heading",{name:"Settings",exact:true})).toBeVisible();
  await expect(modal).toContainText("Support");
  await modal.getByRole("button",{name:"Copy email",exact:true}).click();
  await expect.poll(()=>page.evaluate(()=>navigator.clipboard.readText())).toBe("estoq.sistema@gmail.com");
  await page.keyboard.press("Tab");
  expect(await modal.evaluate(el=>el.contains(document.activeElement))).toBeTruthy();
  await modal.screenshot({path:testInfo.outputPath(`configuracoes-${largura}.png`)});
  const transborda=await modal.evaluate(el=>el.scrollWidth>el.clientWidth+1);
  expect(transborda).toBeFalsy();
  await page.keyboard.press("Escape");
  await expect(busca).toHaveValue("Ingrediente");
  await expect(page.getByText("Ingrediente do restaurante A",{exact:true})).toBeVisible();
  await expect(page.getByText(/R\$|BRL/).first()).toBeVisible();
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("lang","en");
  await expect(page.locator("html")).toHaveCSS("font-size","20px");
  await page.getByRole("button",{name:"Settings",exact:true}).click();
  await page.getByRole("dialog").getByLabel("Language",{exact:true}).selectOption("es");
  await expect(page.getByRole("dialog").getByRole("heading",{name:"Configuración",exact:true})).toBeVisible();
  await page.keyboard.press("Escape");
  expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)).toBeFalsy();
});

test("preferências de uma conta não aparecem na conta seguinte",async({page})=>{
  await preparar(page);
  await page.goto("/estoque");
  await page.getByRole("button",{name:"Configurações",exact:true}).click();
  await page.getByLabel("Tema",{exact:true}).selectOption("escuro");
  await expect(page.locator("html")).toHaveAttribute("data-tema","escuro");
  await page.keyboard.press("Escape");
  await entrarNaLojaB(page);
  await expect(page.locator("html")).toHaveAttribute("data-tema","original");
  await expect(page.locator("html")).toHaveAttribute("lang","pt-BR");
});
