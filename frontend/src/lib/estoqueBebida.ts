export function bebidaPorUnidade(nome: string, categoria?: string | null): boolean {
  const normalizar = (texto: string) => texto.normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toUpperCase().replace(/[^A-Z0-9]/g, " ").replace(/\s+/g, " ").trim();
  const produto = normalizar(nome);
  return /\bBEBIDAS?\b/.test(normalizar(categoria ?? ""))
    || (!produto.startsWith("AGUA SANITARIA")
      && /^(BEB|BEBIDA|REFRI|REFRIG|REFRIGERANTE|SUCO|CERVEJA|VINHO|ENERGETICO|AGUA|H2O|H2OH|COCA COLA|FANTA|SPRITE|PEPSI|GUARANA)( |$)/.test(produto));
}
