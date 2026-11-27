import { traduzir } from "../lib/i18n";
import { Button } from "./UI";
import type { Page } from "../lib/types";

export const ITENS_POR_PAGINA = 30;

export function Paginacao({ dados, pagina, alterar, totalItens, itensPorPagina = ITENS_POR_PAGINA, alterarTamanho }: {
  dados: Pick<Page<unknown>, "totalPages" | "totalElements"> | null;
  pagina: number;
  alterar: (pagina: number) => void;
  totalItens?: number;
  itensPorPagina?: number;
  alterarTamanho?: (tamanho:number)=>void;
}) {
  const totalRegistros = dados?.totalElements ?? totalItens ?? 0;
  const totalPaginas = dados?.totalPages ?? Math.ceil(totalRegistros / itensPorPagina);
  if (totalPaginas <= 1 && !alterarTamanho) return null;
  return <nav className="paginacao" aria-label={traduzir("Paginação da lista")}>
    {alterarTamanho && <label>{" "}{traduzir("Registros por página")}{" "}<select aria-label={traduzir("Registros por página")} value={itensPorPagina} onChange={e=>{alterarTamanho(Number(e.target.value));alterar(0);}}>{[10,25,50].map(n=><option key={n} value={n}>{n}</option>)}</select></label>}
    <Button size="sm" variant="outline" disabled={pagina === 0} onClick={() => alterar(pagina - 1)}>{" "}{traduzir("Anterior")}{" "}</Button>
    <span aria-live="polite">{" "}{traduzir("Página")}{" "}{pagina + 1}{" "}{traduzir("de")}{" "}{Math.max(1,totalPaginas)} · {totalRegistros}{" "}{traduzir("registros")}{" "}</span>
    <Button size="sm" variant="outline" disabled={pagina + 1 >= totalPaginas} onClick={() => alterar(pagina + 1)}>{" "}{traduzir("Próxima")}{" "}</Button>
  </nav>;
}
