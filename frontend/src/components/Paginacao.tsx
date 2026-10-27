import { Button } from "./UI";
import type { Page } from "../lib/types";

export const ITENS_POR_PAGINA = 10;

export function Paginacao({ dados, pagina, alterar, totalItens, itensPorPagina = ITENS_POR_PAGINA }: {
  dados: Pick<Page<unknown>, "totalPages" | "totalElements"> | null;
  pagina: number;
  alterar: (pagina: number) => void;
  totalItens?: number;
  itensPorPagina?: number;
}) {
  const totalRegistros = dados?.totalElements ?? totalItens ?? 0;
  const totalPaginas = dados?.totalPages ?? Math.ceil(totalRegistros / itensPorPagina);
  if (totalPaginas <= 1) return null;
  return <nav className="paginacao" aria-label="Paginação da lista">
    <Button size="sm" variant="outline" disabled={pagina === 0} onClick={() => alterar(pagina - 1)}>Anterior</Button>
    <span aria-live="polite">Página {pagina + 1} de {totalPaginas} · {totalRegistros} registros</span>
    <Button size="sm" variant="outline" disabled={pagina + 1 >= totalPaginas} onClick={() => alterar(pagina + 1)}>Próxima</Button>
  </nav>;
}
