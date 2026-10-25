import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export function LinksPublicos() {
  return <nav className="public-links" aria-label="Informações do EstoQ">
    <Link to="/sobre">Sobre o EstoQ</Link>
    <Link to="/privacidade">Privacidade</Link>
  </nav>;
}

export function PaginaPublica({ children }: { children: ReactNode }) {
  return <div className="public-page">
    <header className="public-page__header">
      <Link to="/sobre" aria-label="EstoQ — página inicial"><img src="/logo_estoq_oficial.png" width={160} height={90} alt="EstoQ" /></Link>
      <Link className="btn btn--ink" to="/login">Entrar</Link>
    </header>
    <main className="public-page__content">{children}</main>
    <footer className="public-page__footer">
      <LinksPublicos />
      <a href="mailto:estoq.sistema@gmail.com">estoq.sistema@gmail.com</a>
    </footer>
  </div>;
}
