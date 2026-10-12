import { Children, Fragment, cloneElement, isValidElement, type ReactNode, type ReactElement } from "react";

/** Os rótulos reutilizam os cabeçalhos; não duplicamos tabelas nem controles. */
function textoDoCabecalho(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (isValidElement<{ children?: ReactNode }>(node)) return textoDoCabecalho(node.props.children);
  return Children.toArray(node).map(textoDoCabecalho).join(" ");
}

export function rotularLinhas(children: ReactNode, headers: ReactNode[]): ReactNode {
  return Children.map(children, linha => {
    if (!isValidElement<{ children?: ReactNode }>(linha)) return linha;
    if (linha.type === Fragment) return cloneElement(linha, {}, rotularLinhas(linha.props.children, headers));
    if (linha.type !== "tr") return linha;
    let coluna = 0;
    return cloneElement(linha, {}, Children.map(linha.props.children, celula => {
      if (!isValidElement<{ colSpan?: number }>(celula) || celula.type !== "td") return celula;
      const label = textoDoCabecalho(headers[coluna]);
      const colSpan = celula.props.colSpan ?? 1;
      coluna += colSpan;
      return cloneElement(celula as ReactElement<Record<string, unknown>>, {
        "data-label": label,
        "data-colspan": colSpan > 1 ? colSpan : undefined,
      });
    }));
  });
}
