import { BrowserRouter, Navigate, Route, Routes, Link } from "react-router-dom";
import { AuthProvider, useAuth } from "./store/auth";
import { ToastProvider } from "./store/toast";
import { Layout } from "./components/Layout";
import { Icon } from "./components/Icon";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Estoque } from "./pages/Estoque";
import { Movimentacoes } from "./pages/Movimentacoes";
import { ProdutosAbertos } from "./pages/ProdutosAbertos";
import { Balanco } from "./pages/Balanco";
import { Catalogo } from "./pages/Catalogo";
import { Relatorios } from "./pages/Relatorios";
import { Backup } from "./pages/Backup";
import { Usuarios } from "./pages/Usuarios";
import type { ReactNode } from "react";

function Splash() {
  return (
    <div className="splash">
      <div className="splash__logo">
        <img src="/logo_small.png" alt="estoQ" width={88} height={58} />
      </div>
      <p className="splash__text">Carregando estoQ…</p>
    </div>
  );
}

function AccessDenied() {
  return (
    <div className="empty" style={{ marginTop: 48 }}>
      <span className="empty__icon" aria-hidden="true">
        <Icon name="alert" size={30} />
      </span>
      <p className="empty__title">Esta área não está disponível para o seu perfil</p>
      <p className="empty__text">
        Cada perfil vê só o que precisa: cozinha movimenta, nutricionista analisa e o
        administrador configura.
      </p>
      <div className="empty__action">
        <Link to="/dashboard" className="btn btn--accent">
          Voltar à visão geral
        </Link>
      </div>
    </div>
  );
}

function Guarded({
  allow,
  children,
}: {
  allow: boolean;
  children: ReactNode;
}) {
  if (!allow) return <AccessDenied />;
  return <>{children}</>;
}

function AppRoutes() {
  const { user, ready, canMove, canReport, isAdmin, isCozinha } = useAuth();

  if (!ready) return <Splash />;
  if (!user) return <Login />;

  return (
    <Layout>
      <Routes>
        <Route
          path="/dashboard"
          element={isCozinha ? <Navigate to="/estoque" replace /> : <Dashboard />}
        />
        <Route path="/estoque" element={<Estoque />} />
        <Route
          path="/movimentacoes"
          element={
            <Guarded allow={canMove}>
              <Movimentacoes />
            </Guarded>
          }
        />
        <Route
          path="/produtos-abertos"
          element={
            <Guarded allow={canMove}>
              <ProdutosAbertos />
            </Guarded>
          }
        />
        <Route
          path="/balanco"
          element={
            <Guarded allow={canMove}>
              <Balanco />
            </Guarded>
          }
        />
        <Route
          path="/relatorios"
          element={
            <Guarded allow={canReport}>
              <Relatorios />
            </Guarded>
          }
        />
        <Route path="/catalogo" element={<Catalogo />} />
        <Route
          path="/usuarios"
          element={
            <Guarded allow={isAdmin}>
              <Usuarios />
            </Guarded>
          }
        />
        <Route
          path="/backup"
          element={
            <Guarded allow={isAdmin}>
              <Backup />
            </Guarded>
          }
        />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Layout>
  );
}

export function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </ToastProvider>
  );
}