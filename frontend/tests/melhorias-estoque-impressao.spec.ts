import { test, expect } from "@playwright/test";
import { preparar } from "./fixtures";

const lote={id:1,version:0,codigo:"LOTE-A",produtoId:1,produtoNome:"Produto <seguro>",unidadeMedida:"KG",dataValidade:"2027-12-31",quantidadeAtual:5,precoUnitario:2,vencido:false};

test("estoque pagina no servidor, reinicia filtros e exporta sem limite de página",async({page})=>{
  await preparar(page);
  const consultas:string[]=[];
  await page.route("**/api/estoque/pagina?**",async route=>{
    const url=new URL(route.request().url());consultas.push(url.search);
    const numero=Number(url.searchParams.get("page"));const size=Number(url.searchParams.get("size"));
    const prox=url.searchParams.get("situacao")==="PROXIMO";
    const total=prox?1:26;
    const content=Array.from({length:Math.min(size,total-numero*size)},(_,i)=>({produtoId:numero*size+i+1,produtoNome:`Produto ${numero*size+i+1}`,categoriaNome:"Grãos",unidadeMedida:"KG",saldoAtual:1,saldoDisponivelConsumo:1,valorEstoque:2,proximoVencimento:prox,possuiLotesVencidos:prox}));
    await route.fulfill({json:{content,totalElements:total,totalPages:Math.ceil(total/size),number:numero,size,valorTotal:total*2}});
  });
  let exportacao="";
  await page.route("**/api/estoque/planilha?**",async route=>{exportacao=route.request().url();await route.fulfill({contentType:"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",body:"teste"});});
  await page.goto("/estoque");
  await page.getByLabel("Registros por página",{exact:true}).selectOption("10");
  await expect(page.getByText(/Página 1 de 3/)).toBeVisible();
  await page.getByRole("button",{name:"Próxima",exact:true}).click();
  await expect(page.getByText("Produto 11",{exact:true})).toBeVisible();
  await page.getByLabel("Situação do estoque").selectOption("PROXIMO");
  await expect(page.getByText(/Página 1 de 1/)).toBeVisible();
  await expect(page.getByRole("row").filter({hasText:"Produto 1"})).toContainText("Vencido");
  await expect(page.getByRole("row").filter({hasText:"Produto 1"})).toContainText("Próximo do vencimento");
  await Promise.all([page.waitForEvent("download"),page.getByRole("button",{name:"Planilha",exact:true}).click()]);
  expect(exportacao).toContain("situacao=PROXIMO");expect(exportacao).not.toMatch(/[?&](page|size)=/);
  expect(consultas.some(q=>q.includes("page=1"))).toBeTruthy();
});

test("balanço conserva contagens entre páginas de itens e balanços",async({page})=>{
  await preparar(page);
  const balanco=(id:number)=>({id,version:0,ativo:true,dataHora:"2026-11-27T10:00:00",tipo:"GERAL",status:"EM_ANDAMENTO",usuarioId:1,usuarioNome:"Admin",categoriaIds:[],categoriaNomes:[],itens:Array.from({length:26},(_,i)=>({id:id*100+i,version:0,produtoId:i+1,produtoNome:`Item ${id}-${i+1}`,unidadeMedida:"KG",quantidadeSistema:1,quantidadeFisica:null,diferenca:null,ajusteAplicado:false}))});
  await page.route("**/api/balancos/pagina?**",async route=>{const url=new URL(route.request().url());const n=Number(url.searchParams.get("page"));const size=Number(url.searchParams.get("size"));await route.fulfill({json:{content:[balanco(n+1)],number:n,size,totalPages:2,totalElements:size+1}});});
  await page.goto("/balanco");
  await page.getByRole("button",{name:"Maximizar balanço #1",exact:true}).click();
  await page.getByLabel("Contagem de Item 1-1",{exact:true}).fill("7,5");
  const interna=page.locator(".contagem .paginacao");
  await interna.getByRole("button",{name:"Próxima",exact:true}).click();
  await expect(page.getByLabel("Contagem de Item 1-26",{exact:true})).toBeVisible();
  await interna.getByRole("button",{name:"Anterior",exact:true}).click();
  await expect(page.getByLabel("Contagem de Item 1-1",{exact:true})).toHaveValue("7,5");
  await page.locator(".paginacao").last().getByRole("button",{name:"Próxima",exact:true}).click();
  await expect(page.getByRole("button",{name:"Maximizar balanço #2",exact:true})).toBeVisible();
  await page.locator(".paginacao").last().getByRole("button",{name:"Anterior",exact:true}).click();
  await expect(page.getByLabel("Contagem de Item 1-1",{exact:true})).toHaveValue("7,5");
});

test("consumo imprime todos os registros do dia e o mesmo custo histórico da tela",async({page})=>{
  await preparar(page);
  await page.route("**/api/movimentacoes?**",route=>{
    const url=new URL(route.request().url());const vazio=url.searchParams.get("dataInicio")==="2026-01-01";
    return route.fulfill({json:vazio?[]:Array.from({length:35},(_,i)=>({id:i+1,tipo:"CONSUMO",dataHora:"2026-11-26T12:00:00",produtoId:i+1,produtoNome:`Produto ${i+1}`,unidadeMedida:"KG",quantidade:1,custoConsumo:2,loteCodigo:`L${i}`,usuarioNome:"Responsável"}))});
  });
  await page.goto("/consumo");
  await expect(page.getByText(/Custo registrado:/)).toContainText("70,00");
  await expect(page.getByRole("table",{name:"Consumos registrados no dia"}).locator("tbody tr")).toHaveCount(30);
  const popupPromise=page.waitForEvent("popup");
  await page.getByRole("button",{name:"Imprimir lista do dia",exact:true}).click();
  const popup=await popupPromise;
  await expect(popup.locator("tbody tr")).toHaveCount(35);
  await expect(popup.locator("body")).toContainText("Total: R$ 70,00");
  await expect(popup.getByRole("navigation")).toHaveCount(0);
  await page.getByLabel("Dia do consumo",{exact:true}).fill("2026-01-01");
  await expect(page.getByText(/Custo registrado:/)).toContainText("0,00");
  await expect(page.getByRole("button",{name:"Imprimir lista do dia",exact:true})).toBeDisabled();
});

test("etiquetas usam o lote salvo, geram prévia e reimpressão sem gravar entrada",async({page},testInfo)=>{
  await preparar(page,"OPERACIONAL");let gravacoes=0;
  await page.route("**/api/lotes/pagina?**",route=>route.fulfill({json:{content:[lote],number:0,size:25,totalPages:1,totalElements:1}}));
  await page.route("**/api/lotes/1",route=>route.fulfill({json:lote}));
  await page.route("**/api/entradas",route=>{gravacoes++;return route.fulfill({json:{movimentacoes:[]}});});
  await page.goto("/entradas");
  await page.getByRole("button",{name:"Imprimir etiqueta",exact:true}).click();
  const modal=page.getByRole("dialog");
  await modal.getByLabel("Quantidade de etiquetas").fill("3");
  const previa=page.frameLocator('iframe[title="Prévia das etiquetas"]');
  await expect(previa.locator(".etiqueta")).toHaveCount(3);
  await expect(previa.locator(".etiqueta").first()).toContainText("Produto <seguro>");
  await expect(previa.locator("script")).toHaveCount(0);
  const popupPromise=page.waitForEvent("popup");
  await modal.getByRole("button",{name:"Imprimir etiqueta",exact:true}).click();
  const popup=await popupPromise;
  await expect(popup.locator(".etiqueta")).toHaveCount(3);
  await popup.emulateMedia({media:"print"});
  await popup.pdf({path:testInfo.outputPath("etiquetas.pdf"),preferCSSPageSize:true,printBackground:true});
  await popup.screenshot({path:testInfo.outputPath("etiquetas.png")});
  const geometria=await popup.locator(".etiqueta").first().evaluate(el=>({width:el.getBoundingClientRect().width,height:el.getBoundingClientRect().height,overflow:el.querySelector(".conteudo")!.scrollHeight>el.querySelector(".conteudo")!.clientHeight+1}));
  expect(geometria.width).toBeCloseTo(60*96/25.4,0);expect(geometria.height).toBeCloseTo(40*96/25.4,0);expect(geometria.overflow).toBeFalsy();
  await popup.close();await page.keyboard.press("Escape");
  await page.getByRole("button",{name:"Imprimir etiqueta",exact:true}).click();
  await expect(page.frameLocator('iframe[title="Prévia das etiquetas"]').locator(".etiqueta")).toHaveCount(1);
  expect(gravacoes).toBe(0);
});
