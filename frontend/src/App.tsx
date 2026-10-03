import { BrowserRouter, Navigate, Route, Routes, Link } from "react-router-dom";
import { AuthProvider, useAuth } from "./store/auth";
import { ToastProvider } from "./store/toast";
import { Layout } from "./components/Layout";
import { Icon } from "./components/Icon";
import { Login } from "./pages/Login";
import { Registro } from "./pages/Registro";
import { Plataforma } from "./pages/Plataforma";
import { Dashboard } from "./pages/Dashboard";
import { Estoque } from "./pages/Estoque";
import { Movimentacoes } from "./pages/Movimentacoes";
import { Consumo } from "./pages/Consumo";
import { Desperdicio } from "./pages/Desperdicio";
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
  const {
    user,
    ready,
    canMove,
    canReport,
    canSeeEstoque,
    canSeeCatalogo,
    isAdmin,
    isCozinha,
    isPlataforma,
  } = useAuth();

  if (!ready) return <Splash />;

  // Sem sessão: só as telas públicas de entrada e cadastro.
  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/registro" element={<Registro />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  // PLATAFORMA governa as cozinhas; não tem dados de uma loja específica.
  if (isPlataforma) {
    return (
      <Layout>
        <Routes>
          <Route path="/plataforma" element={<Plataforma />} />
          <Route path="*" element={<Navigate to="/plataforma" replace />} />
        </Routes>
      </Layout>
    );
  }

  return (
    <Layout>
      <Routes>
        <Route
          path="/dashboard"
          element={isCozinha ? <Navigate to="/estoque" replace /> : <Dashboard />}
        />
        <Route
          path="/estoque"
          element={
            <Guarded allow={canSeeEstoque}>
              <Estoque />
            </Guarded>
          }
        />
        <Route
          path="/movimentacoes"
          element={
            <Guarded allow={canMove}>
              <Movimentacoes />
            </Guarded>
          }
        />
        <Route
          path="/consumo"
          element={
            <Guarded allow={canMove}>
              <Consumo />
            </Guarded>
          }
        />
        <Route
          path="/desperdicio"
          element={
            <Guarded allow={canMove}>
              <Desperdicio />
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
        <Route
          path="/catalogo"
          element={
            <Guarded allow={canSeeCatalogo}>
              <Catalogo />
            </Guarded>
          }
        />
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