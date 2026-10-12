import { useEffect, useRef, type ReactNode } from "react";

/** A altura acompanha as oito primeiras linhas, mesmo quando viram cartões. */
export function ListaComRolagem({ quantidade, children }: { quantidade: number; children: ReactNode }) {
  const area = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const container = area.current?.querySelector<HTMLDivElement>(".table-wrap");
    const tabela = container?.querySelector<HTMLTableElement>("table");
    if (!container || !tabela) return;
    const ajustarAltura = () => {
      const oitavo = tabela.tBodies[0]?.rows[7];
      if (quantidade <= 8 || !oitavo) { container.style.maxHeight = ""; return; }
      const bordaEBarra = container.offsetHeight - container.clientHeight - container.clientTop;
      const altura = oitavo.getBoundingClientRect().bottom - container.getBoundingClientRect().top
        + container.scrollTop + bordaEBarra;
      container.style.maxHeight = `${Math.ceil(altura)}px`;
    };
    ajustarAltura();
    const observer = new ResizeObserver(ajustarAltura);
    observer.observe(tabela);
    return () => observer.disconnect();
  }, [quantidade]);
  return <div ref={area} className={quantidade > 8 ? "relatorio-lista-rolagem" : ""}>{children}</div>;
}
