import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export function LinksPublicos() {
  return <nav className="public-links" aria-label="Informações do EstoQ">
    <Link to="/sobre">Sobre o EstoQ</Link>
    <Link to="/privacidade">Privacidade</Link>
  </nav>;
}

export function CabecalhoPublico() {
  return <header className="public-page__header">
    <Link to="/" aria-label="EstoQ — página inicial"><img src="/logo_estoq_oficial.png" width={160} height={90} alt="EstoQ" /></Link>
    <Link className="btn btn--ink" to="/login">Entrar</Link>
  </header>;
}

export function RodapePublico() {
  return <footer className="public-page__footer">
    <LinksPublicos />
    <a href="mailto:estoq.sistema@gmail.com">estoq.sistema@gmail.com</a>
  </footer>;
}

export function PaginaPublica({ children, wide }: { children: ReactNode; wide?: boolean }) {
  return <div className="public-page">
    <CabecalhoPublico />
    <main className={wide ? "public-page__content public-page__content--wide" : "public-page__content"}>{children}</main>
    <RodapePublico />
  </div>;
}
