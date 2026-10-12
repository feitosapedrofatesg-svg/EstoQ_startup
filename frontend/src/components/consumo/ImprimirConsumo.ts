import { fmtDate, fmtNum, fmtDateTime } from "../../lib/format";

export interface LinhaConsumoImpressao { produto: string; quantidade: number; unidade: string }

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
  doc.title = conferencia ? "Lista de consumo para conferência" : "Consumo do dia";
  doc.documentElement.lang = "pt-BR";
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
  doc.body.className = "consumo-impressao";
  const texto = (tag: string, valor: string, pai: HTMLElement = doc.body) => {
    const elemento = doc.createElement(tag); elemento.textContent = valor; pai.appendChild(elemento); return elemento;
  };
  const cabecalhoDocumento = doc.createElement("header");
  cabecalhoDocumento.className = "relatorio-cabecalho"; doc.body.appendChild(cabecalhoDocumento);
  texto("div", "EstoQ · Relatórios", cabecalhoDocumento).className = "relatorio-marca";
  texto("h1", conferencia ? "Lista para conferência — consumo ainda não registrado" : "Consumo do dia", cabecalhoDocumento);
  const metadados = doc.createElement("div"); metadados.className = "relatorio-metadados"; cabecalhoDocumento.appendChild(metadados);
  texto("p", restaurante, metadados);
  texto("p", `Data consultada: ${fmtDate(dia)}`, metadados);
  texto("p", `Emissão: ${fmtDateTime(new Date().toISOString())}`, metadados);
  if (conferencia) texto("p", "CONFERÊNCIA: o consumo ainda não foi registrado no estoque.").className = "relatorio-aviso";
  const tabela = doc.createElement("table"); doc.body.appendChild(tabela);
  tabela.createCaption().textContent = "Itens de consumo";
  const colunas = doc.createElement("colgroup");
  ["produto", "quantidade", "unidade"].forEach(nome => { const coluna = doc.createElement("col"); coluna.className = `relatorio-coluna-${nome}`; colunas.appendChild(coluna); });
  tabela.appendChild(colunas);
  const cabecalho = tabela.createTHead().insertRow();
  ["Produto", "Quantidade", "Unidade"].forEach(titulo => { const celula = texto("th", titulo, cabecalho); celula.setAttribute("scope", "col"); });
  const corpo = tabela.createTBody();
  linhas.forEach(linha => {
    const row = corpo.insertRow();
    [linha.produto, fmtNum(linha.quantidade), linha.unidade.toLowerCase()].forEach(valor => texto("td", valor, row));
  });
  texto("p", `${linhas.length} ${linhas.length === 1 ? "produto" : "produtos"}${conferencia ? " · Esta lista não comprova uma baixa de estoque." : " · Quantidades consolidadas por produto e unidade."}`).className = "relatorio-fechamento";
  // A impressão continua disponível mesmo se uma fonte demorar a responder.
  const aguardarComLimite = (pronto: Promise<unknown>) => Promise.race([
    pronto, new Promise<void>(resolve => janela.setTimeout(resolve, 3000)),
  ]);
  void aguardarComLimite(Promise.all(estilosProntos)).then(() => aguardarComLimite(doc.fonts.ready)).then(() => janela.requestAnimationFrame(() => janela.requestAnimationFrame(() => {
    if (!janela.closed) { janela.focus(); janela.print(); }
  })));
}
