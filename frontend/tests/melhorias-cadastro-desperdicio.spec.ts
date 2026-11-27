import {test,expect} from "@playwright/test";
import {preparar,prepararDocumentos} from "./fixtures";

test("cadastro exige confirmações separadas e permite ler os documentos antes",async({page})=>{
 await prepararDocumentos(page);
 await page.route("**/api/auth/me",r=>r.fulfill({status:401,json:{message:"Autenticação necessária."}}));
 await page.route("**/api/auth/csrf",r=>r.fulfill({json:{token:"teste",headerName:"X-CSRF-TOKEN"}}));
 await page.route("**/api/auth/google/configuracao",r=>r.fulfill({json:{habilitado:false}}));
 let envios=0;
 await page.route("**/api/registro",r=>{envios++;return r.fulfill({json:{confirmacaoEmailPendente:false}});});
 await page.goto("/registro");
 await page.locator("#reg-loja").fill("Restaurante de teste");
 await page.locator("#reg-nome").fill("Responsável");
 await page.locator("#reg-email").fill("pessoa@teste.com");
 await page.locator("#reg-senha").fill("Teste@12345");
 await page.locator("#reg-confirmar-senha").fill("Teste@12345");
 const termos=page.getByRole("checkbox",{name:"Li e aceito os Termos de Uso.",exact:true});
 const politica=page.getByRole("checkbox",{name:"Li e estou ciente da Política de Privacidade.",exact:true});
 await expect(termos).not.toBeChecked();await expect(politica).not.toBeChecked();
 const cadastrar=page.getByRole("button",{name:"Criar conta",exact:true});
 await expect(cadastrar).toBeDisabled();await termos.check();await expect(cadastrar).toBeDisabled();
 const nova=page.waitForEvent("popup");await page.getByRole("link",{name:"Termos de Uso completos",exact:true}).click();
 const documento=await nova;await expect(documento.locator('[data-documento="termos"]')).toContainText("Minuta para revisão");
 await documento.close();await politica.check();await expect(cadastrar).toBeEnabled();
 expect(envios).toBe(0);await cadastrar.click();await expect.poll(()=>envios).toBe(1);
});

test("desperdício mostra saldo por lote e recarrega após baixa sem excluir produto",async({page})=>{
 await preparar(page);let saldo=5;let baixas=0;
 const lote=()=>({id:1,version:0,codigo:"VENCIDO-A",produtoId:1,produtoNome:"Arroz",unidadeMedida:"KG",dataValidade:"2020-01-01",quantidadeAtual:saldo,precoUnitario:2,vencido:true});
 await page.route("**/api/estoque",r=>r.fulfill({json:[{produtoId:1,produtoNome:"Arroz",categoriaNome:"Grãos",unidadeMedida:"KG",saldoAtual:saldo,saldoDisponivelConsumo:0}]}));
 await page.route("**/api/produtos/1",r=>r.fulfill({json:{id:1,nome:"Arroz",unidadeMedida:"KG"}}));
 await page.route("**/api/lotes?**",r=>r.fulfill({json:[lote()]}));
 await page.route("**/api/lotes/pagina?**",r=>r.fulfill({json:{content:saldo?[lote()]:[],totalPages:saldo?1:0,totalElements:saldo?1:0,number:0,size:25}}));
 await page.route("**/api/desperdicios",r=>{expect(r.request().method()).toBe("POST");saldo-=r.request().postDataJSON().quantidade;baixas++;return r.fulfill({json:{produtoId:1,saldoAtual:saldo,movimentacoes:[]}});});
 await page.route("**/api/produtos/1",r=>{expect(r.request().method()).toBe("GET");return r.fulfill({json:{id:1,nome:"Arroz",unidadeMedida:"KG"}});});
 await page.goto("/desperdicio?produto=1");
 await expect(page.getByRole("status").filter({hasText:"Saldo disponível:"})).toContainText("5 kg");
 await page.getByRole("button",{name:"Maximizar Itens para excluir do estoque",exact:true}).click();
 await page.getByRole("button",{name:"Excluir do estoque",exact:true}).click();
 const modal=page.getByRole("dialog");await expect(modal.getByRole("status")).toContainText("5 kg");
 await modal.getByRole("textbox",{name:"Quantidade descartada",exact:true}).fill("2");
 await modal.getByRole("button",{name:"Confirmar exclusão do estoque",exact:true}).click();
 await expect(modal).toHaveCount(0);
 await expect(page.getByRole("status").filter({hasText:"Saldo disponível:"})).toContainText("3 kg");
 expect(baixas).toBe(1);
});
