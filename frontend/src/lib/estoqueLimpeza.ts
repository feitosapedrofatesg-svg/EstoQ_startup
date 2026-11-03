export function limpezaPorUnidade(nome: string, categoria?: string | null): boolean {
  const normalizar = (s: string) => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().replace(/[^A-Z0-9]/g, " ").replace(/\s+/g, " ").trim();
  return /\b(LIMPEZA|HIGIENE)\b/.test(normalizar(categoria ?? "")) || /^(ESPONJA|DETERGENTE|DETERG|DESINFETANTE|DESINF|SABAO|SABONETE|ALVEJANTE|LIMPADOR|DESENGORDURANTE|AMACIANTE|AGUA SANITARIA|ALCOOL (LIQ|LIQUIDO|GEL)|SACO (DE |PARA )?LIXO|PALHA DE ACO|LA DE ACO|CREME DENTAL|PAPEL HIGIENICO)( |$)/.test(normalizar(nome));
}
export function unidadesLimpeza(descricao: string): number | null {
  const valores = [...descricao.matchAll(/(?:\bC\s*\/\s*|\bCOM\s+)(\d+)\b|\b(\d+)\s*(?:UN|UND|UNID|UNIDADES)\b/gi)].map(m => Number(m[1] ?? m[2]));
  return valores.some(v => v <= 0 || v !== valores[0]) ? null : valores[0] ?? 1;
}
