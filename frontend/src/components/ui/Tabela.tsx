import { type ReactNode } from "react";



import { rotularLinhas } from "../../responsividade/celular/TabelaResponsiva";





export function DataTable({
  headers,
  children,
  caption,
}: {
  headers: ReactNode[];
  children: ReactNode;
  caption?: string;
}) {
  return (
    <div className="table-wrap table-wrap--responsiva">
      <table className="table">
        {caption && <caption className="visually-hidden">{caption}</caption>}
        <thead>
          <tr>
            {headers.map((h, i) => (
              <th key={i} scope="col">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{rotularLinhas(children, headers)}</tbody>
      </table>
    </div>
  );
}
