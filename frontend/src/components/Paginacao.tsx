import { Button } from "./UI";
import type { Page } from "../lib/types";

export function Paginacao({ dados, pagina, alterar }: {
  dados: Page<unknown> | null; pagina: number; alterar: (pagina: number) => void;
}) {
  if (!dados || dados.totalPages <= 1) return null;
  return <nav className="paginacao" aria-label="Paginação da lista">
    <Button size="sm" variant="outline" disabled={pagina === 0} onClick={() => alterar(pagina - 1)}>Anterior</Button>
    <span aria-live="polite">Página {pagina + 1} de {dados.totalPages} · {dados.totalElements} registros</span>
    <Button size="sm" variant="outline" disabled={pagina + 1 >= dados.totalPages} onClick={() => alterar(pagina + 1)}>Próxima</Button>
  </nav>;
}
