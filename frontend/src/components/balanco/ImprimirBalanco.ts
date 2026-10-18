import { fmtDateTime, fmtNum } from "../../lib/format";
import type { BalancoDTO } from "../../lib/types";

/** Nomes informados pelos usuários são texto, nunca HTML da impressão. */
export function imprimirBalanco(balanco: BalancoDTO) {
  const janela = window.open("", "_blank", "width=850,height=650");
  if (!janela) throw new Error("Permita a abertura de janelas para imprimir o balanço.");
  const doc = janela.document;
  doc.open();
  doc.write('<!doctype html><html lang="pt-BR"><head></head><body></body></html>');
  doc.close();
  doc.title = `Balanço #${balanco.id}`;
  const estilo = doc.createElement("style");
  estilo.textContent = `body{font-family:system-ui,sans-serif;padding:24px;color:#1a1a2e}
    table{width:100%;border-collapse:collapse;font-size:13px} th,td{padding:6px;text-align:left;border-bottom:1px solid #ddd}
    td:nth-child(n+2){text-align:right} p{font-size:13px} @media print{body{padding:12px}}`;
  doc.head.appendChild(estilo);
  const texto = (tag: string, valor: string, pai: HTMLElement = doc.body) => {
    const elemento = doc.createElement(tag);
    elemento.textContent = valor;
    pai.appendChild(elemento);
    return elemento;
  };
  const status = { PENDENTE: "Aguardando início", EM_ANDAMENTO: "Em contagem", CONCLUIDO: "Concluído" };
  texto("h1", `Balanço #${balanco.id}`);
  texto("p", `${status[balanco.status]} — ${fmtDateTime(balanco.dataHora)} — ${balanco.tipo} — ${balanco.usuarioNome}`);
  if (balanco.tipo === "PARCIAL" && balanco.categoriaNomes?.length) {
    texto("p", `Categorias: ${balanco.categoriaNomes.join(", ")}`);
  }
  const tabela = doc.createElement("table");
  doc.body.appendChild(tabela);
  const cabecalho = tabela.createTHead().insertRow();
  const colunas = ["Produto", "Sistema", "Contado", "Diferença"];
  if (balanco.status === "CONCLUIDO") colunas.push("Ajustado");
  colunas.forEach(titulo => texto("th", titulo, cabecalho).setAttribute("scope", "col"));
  const corpo = tabela.createTBody();
  for (const item of balanco.itens) {
    const unidade = item.unidadeMedida.toLowerCase();
    const valores = [item.produtoNome, `${fmtNum(item.quantidadeSistema)} ${unidade}`,
      item.quantidadeFisica === null ? "—" : `${fmtNum(item.quantidadeFisica)} ${unidade}`,
      item.diferenca === null ? "—" : `${item.diferenca > 0 ? "+" : ""}${fmtNum(item.diferenca)} ${unidade}`];
    if (balanco.status === "CONCLUIDO") valores.push(item.ajusteAplicado ? "Ajustado" : "Pendente");
    const linha = corpo.insertRow();
    valores.forEach(valor => texto("td", valor, linha));
  }
  const contados = balanco.itens.filter(item => item.quantidadeFisica !== null).length;
  texto("p", `${contados} de ${balanco.itens.length} itens contados — gerado em ${new Date().toLocaleString("pt-BR")}`);
  janela.setTimeout(() => { if (!janela.closed) { janela.focus(); janela.print(); } }, 300);
}
