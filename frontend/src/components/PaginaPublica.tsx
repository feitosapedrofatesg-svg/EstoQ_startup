import { traduzir } from "../lib/i18n";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export function LinksPublicos() {
  return <nav className="public-links" aria-label={traduzir("Informações do EstoQ")}>
    <Link to="/sobre">{" "}{traduzir("Sobre o EstoQ")}{" "}</Link>
  </nav>;
}

export function CabecalhoPublico() {
  return <header className="public-page__header">
    <Link to="/" aria-label={traduzir("EstoQ — página inicial")}><img src="/logo_estoq_oficial.png" width={160} height={90} alt={traduzir("EstoQ")} /></Link>
    <Link className="btn btn--ink" to="/login">{" "}{traduzir("Entrar")}{" "}</Link>
  </header>;
}

export function RodapePublico() {
  return <footer className="public-page__footer">
    <LinksPublicos />
  </footer>;
}

export function PaginaPublica({ children, wide }: { children: ReactNode; wide?: boolean }) {
  return <div className="public-page">
    <CabecalhoPublico />
    <main className={wide ? "public-page__content public-page__content--wide" : "public-page__content"}>{children}</main>
    <RodapePublico />
  </div>;
}
