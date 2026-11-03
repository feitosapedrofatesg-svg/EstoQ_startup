import { bebidaPorUnidade } from "./estoqueBebida";
import { limpezaPorUnidade } from "./estoqueLimpeza";
export function estoquePorUnidade(nome: string, categoria?: string | null): boolean {
  return bebidaPorUnidade(nome, categoria) || limpezaPorUnidade(nome, categoria);
}
