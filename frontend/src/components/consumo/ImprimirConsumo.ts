import { traduzir } from "../../lib/i18n";
import { fmtDate, fmtNum, fmtDateTime, fmtMoney } from "../../lib/format";

export interface LinhaConsumoImpressao { produto: string; quantidade: number; unidade: string; custo?: number }

/** A impressão usa texto no DOM, sem interpretar nomes de produtos como HTML. */
export function imprimirConsumo(restaurante: string, dia: string, linhas: LinhaConsumoImpressao[], conferencia = false) {
  if (!linhas.length) return;
  const janela = window.open("", "_blank", "width=850,height=650");
  if (!janela) throw new Error("O navegador bloqueou a impressão. Permita a abertura de janelas para este site.");
  const doc = janela.document;
  // Finalizamos o documento vazio antes de carregar estilos e fontes na janela.
  doc.open();
  doc.write('<!doctype html><html lang="pt-BR"><head></head><body></body></html>');
  doc.close();
  doc.title = traduzir(conferencia ? "Lista de consumo para conferência" : "Consumo do dia");
  doc.documentElement.lang = document.documentElement.lang;
  // Reutilizamos as mesmas fontes e estilos, inclusive no frontend publicado.
  const estilosProntos: Promise<void>[] = [];
  document.querySelectorAll('link[rel="stylesheet"], style').forEach(estilo => {
    const copia = estilo.cloneNode(true) as HTMLElement;
    if (copia.tagName === "LINK") estilosProntos.push(new Promise(resolve => {
      copia.addEventListener("load", () => resolve(), { once: true });
      copia.addEventListener("error", () => resolve(), { once: true });
    }));
    doc.head.appendChild(copia);
  });
  const estilo=doc.createElement("style");
  estilo.textContent="html,body{background:#fff!important;color:#111!important;font-size:12pt!important} table{width:100%;border-collapse:collapse}th,td{color:#111!important;background:white!important;border-bottom:1px solid #bbb;padding:6pt;text-align:left}thead{display:table-header-group}tr{break-inside:avoid}@page{margin:15mm}";
  doc.head.appendChild(estilo);
  doc.body.className = "consumo-impressao";
  const texto = (tag: string, valor: string, pai: HTMLElement = doc.body) => {
    const elemento = doc.createElement(tag); elemento.textContent = valor; pai.appendChild(elemento); return elemento;
  };
  const cabecalhoDocumento = doc.createElement("header");
  cabecalhoDocumento.className = "relatorio-cabecalho"; doc.body.appendChild(cabecalhoDocumento);
  texto("div", traduzir("EstoQ · Relatórios"), cabecalhoDocumento).className = "relatorio-marca";
  texto("h1", traduzir(conferencia ? "Lista para conferência — consumo ainda não registrado" : "Consumo do dia"), cabecalhoDocumento);
  const metadados = doc.createElement("div"); metadados.className = "relatorio-metadados"; cabecalhoDocumento.appendChild(metadados);
  texto("p", restaurante, metadados);
  texto("p", traduzir("Data consultada: {0}",{0:fmtDate(dia)}), metadados);
  texto("p", traduzir("Emissão: {0}",{0:fmtDateTime(new Date().toISOString())}), metadados);
  if (conferencia) texto("p", traduzir("CONFERÊNCIA: o consumo ainda não foi registrado no estoque.")).className = "relatorio-aviso";
  const tabela = doc.createElement("table"); doc.body.appendChild(tabela);
  tabela.createCaption().textContent = traduzir("Itens de consumo");
  const colunas = doc.createElement("colgroup");
  (conferencia ? ["produto", "quantidade", "unidade"] : ["produto", "quantidade", "unidade","custo"]).forEach(nome => { const coluna = doc.createElement("col"); coluna.className = `relatorio-coluna-${nome}`; colunas.appendChild(coluna); });
  tabela.appendChild(colunas);
  const cabecalho = tabela.createTHead().insertRow();
  (conferencia ? ["Produto", "Quantidade", "Unidade"] : ["Produto", "Quantidade", "Unidade","Custo"]).forEach(titulo => { const celula = texto("th", traduzir(titulo), cabecalho); celula.setAttribute("scope", "col"); });
  const corpo = tabela.createTBody();
  linhas.forEach(linha => {
    const row = corpo.insertRow();
    const valores=[linha.produto,fmtNum(linha.quantidade),linha.unidade.toLowerCase()];
    if(!conferencia)valores.push(fmtMoney(linha.custo ?? 0));
    valores.forEach(valor=>texto("td",valor,row));
  });
  if(!conferencia)texto("p",traduzir("Total: {0}",{0:fmtMoney(linhas.reduce((a,l)=>a+(l.custo ?? 0),0))})).className="relatorio-fechamento";
  texto("p", `${linhas.length} ${traduzir(linhas.length === 1 ? "produto" : "produtos")} · ${traduzir(conferencia ? "Esta lista não comprova uma baixa de estoque." : "Quantidades consolidadas por produto e unidade.")}`).className = "relatorio-fechamento";
  // A impressão continua disponível mesmo se uma fonte demorar a responder.
  const aguardarComLimite = (pronto: Promise<unknown>) => Promise.race([
    pronto, new Promise<void>(resolve => janela.setTimeout(resolve, 3000)),
  ]);
  void aguardarComLimite(Promise.all(estilosProntos)).then(() => aguardarComLimite(doc.fonts.ready)).then(() => janela.requestAnimationFrame(() => janela.requestAnimationFrame(() => {
    if (!janela.closed) { janela.focus(); janela.print(); }
  })));
}
