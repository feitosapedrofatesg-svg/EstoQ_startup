import { mensagemErro } from "../lib/api";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { NavLink, Link } from "react-router-dom";
import { useAuth } from "../store/auth";
import { api } from "../lib/api";
import type { AlertaDTO, Perfil } from "../lib/types";
import { PERFIL_LABEL } from "../lib/perfis";
import { Icon, type IconName } from "./Icon";
import { fmtDateTime, diasAtrasISO, hojeISO } from "../lib/format";
import { useToast } from "../store/toast";
import { prefetchar, useFetch, updateCache } from "../lib/hooks";
import { assinarFalhasCache, possuiFalhasCache, tentarNovamenteCache } from "../lib/cache";
import { Button } from "./UI";
import { iniciarSincronizacao } from "../lib/sincronizacao";

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  end?: boolean;
}

interface NavGroup {
  heading: string;
  items: NavItem[];
}

function buildNav(perfil: Perfil): NavGroup[] {
  if (perfil === "PLATAFORMA") {
    return [
      {
        heading: "Plataforma",
        items: [{ to: "/plataforma", label: "Restaurantes", icon: "store", end: true },
          { to: "/backup", label: "Backup completo", icon: "archive" }],
      },
    ];
  }
  // O dashboard fica disponível para ADMIN e NUTRICIONISTA. A cozinha cai
  // direto no estoque porque não usa o dashboard de visão geral.
  const operacao: NavItem[] = [];
  if (perfil === "ADMIN") {
    operacao.push(
      { to: "/dashboard", label: "Visão geral", icon: "dashboard", end: true },
      { to: "/estoque", label: "Estoque", icon: "boxes" },
      { to: "/consumo", label: "Consumo", icon: "check-circle" },
      { to: "/desperdicio", label: "Desperdício", icon: "trash" },
      { to: "/movimentacoes", label: "Movimentações", icon: "arrows" },
      { to: "/balanco", label: "Balanço físico", icon: "scale" }
    );
  } else if (perfil === "COZINHA") {
    operacao.push(
      { to: "/estoque", label: "Estoque", icon: "boxes" },
      { to: "/consumo", label: "Consumo", icon: "check-circle" },
      { to: "/desperdicio", label: "Desperdício", icon: "trash" },
      { to: "/balanco", label: "Balanço físico", icon: "scale" }
    );
  } else {
    operacao.push({ to: "/dashboard", label: "Visão geral", icon: "dashboard", end: true });
  }

  const groups: NavGroup[] = [{ heading: "Operação", items: operacao }];

  if (perfil === "ADMIN" || perfil === "NUTRICIONISTA") {
    groups.push({
      heading: "Análise",
      items: [{ to: "/relatorios", label: "Relatórios e CMV", icon: "chart" }],
    });
  }
  if (perfil === "ADMIN") {
    groups.push({
      heading: "Administração",
      items: [
        { to: "/catalogo", label: "Catálogo", icon: "tags" },
        { to: "/usuarios", label: "Usuários", icon: "users" },
      ],
    });
  } else if (perfil === "COZINHA") {
    groups.push({
      heading: "Consulta",
      items: [{ to: "/catalogo", label: "Catálogo", icon: "tags" }],
    });
  }
  return groups;
}

function Brand() {
  return (
    <Link to="/dashboard" className="brand" aria-label="estoQ — início">
      <span className="brand__logo">
        <img src="/logo_estoq_oficial.png" alt="estoQ — Controle o desperdício do seu restaurante" width={220} height={124} />
      </span>
    </Link>
  );
}

function RoleLabel({ perfil }: { perfil: Perfil }) {
  return <span className="role-tag role-tag--plataforma">{PERFIL_LABEL[perfil]}</span>;
}

function AlertBell() {
  const [open, setOpen] = useState(false);
  const { data: total } = useFetch<number>("/api/alertas/abertos");
  const { data: lista, loading, setData } = useFetch<AlertaDTO[]>(open ? "/api/alertas?visualizado=false" : null);
  const [vistos, setVistos] = useState<Set<number>>(new Set());
  const [marcando, setMarcando] = useState<number | null>(null);
  const trava = useRef(false);
  const liberar = useRef<number>();
  const alerts = (lista ?? []).filter(a => !vistos.has(a.id));
  const count = open && lista ? alerts.length : total ?? 0;
  const boxRef = useRef<HTMLDivElement>(null);
  const toast = useToast();

  const openPanel = () => setOpen(o => !o);

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  const markSeen = async (id: number) => {
    // A trava síncrona protege também dois cliques antes de o React atualizar o botão.
    if (trava.current) return;
    trava.current = true;
    setMarcando(id);
    // A leitura do alerta é reversível: retire já da tela e restaure se a gravação falhar.
    setVistos(prev => new Set(prev).add(id));
    try {
      await api.put(`/api/alertas/${id}/visualizado`);
      const remover = (dados: AlertaDTO[]) => dados.filter(a => a.id !== id);
      setData(prev => prev ? remover(prev) : prev);
      updateCache<AlertaDTO[]>("/api/alertas?visualizado=false", remover);
    } catch (e) {
      setVistos(prev => { const atual = new Set(prev); atual.delete(id); return atual; });
      toast.error("Não foi possível atualizar", mensagemErro(e));
    } finally {
      // A linha seguinte muda de posição: não deixe o segundo clique atingir outro alerta.
      liberar.current = window.setTimeout(() => { trava.current = false; setMarcando(null); }, 500);
    }
  };

  useEffect(() => () => window.clearTimeout(liberar.current), []);

  return (
    <div className="bell" ref={boxRef}>
      <button
        className="bell__btn"
        onClick={openPanel}
        aria-label={`Alertas${count ? `, ${count} novos` : ""}`}
        aria-expanded={open}
      >
        <Icon name="bell" size={20} />
        {count > 0 && <span className="bell__badge" aria-hidden="true" />}
      </button>
      {open && (
        <div className="bell__panel" role="dialog" aria-label="Central de alertas">
          <p className="bell__title">Central de alertas</p>
          {marcando !== null && <p className="muted" role="status">Marcando alerta como visto…</p>}
          {loading ? (
            <p className="muted" style={{ padding: "12px 0" }}>
              Carregando…
            </p>
          ) : alerts.length === 0 ? (
            <p className="muted" style={{ padding: "12px 0" }}>
              Nada em aberto. Tudo tranquilo por aqui.
            </p>
          ) : (
            <ul className="bell__list">
              {alerts.map((a) => (
                <li key={a.id} className="bell__item">
                  <div className="bell__item-body">
                    <p className="bell__msg">{a.mensagem}</p>
                    <p className="bell__time">{fmtDateTime(a.dataGeracao)}</p>
                  </div>
                  <button className="bell__seen" disabled={marcando !== null} onClick={() => markSeen(a.id)}>
                    {marcando === a.id ? "Marcando…" : "Marcar como visto"}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout, isAdmin, isCozinha } = useAuth();
  const [navOpen, setNavOpen] = useState(false);
  const [falhaCache, setFalhaCache] = useState(possuiFalhasCache);
  useEffect(() => assinarFalhasCache(() => setFalhaCache(possuiFalhasCache())), []);
  useEffect(() => {
    if (user && user.perfil !== "PLATAFORMA" && user.restauranteId) return iniciarSincronizacao();
  }, [user?.id, user?.restauranteId, user?.perfil]);

  // Pré-carrega no cache SWR as telas principais do perfil, pra troca de
  // página não re-buscar tudo do zero. O dashboard é só de ADMIN e
  // NUTRICIONISTA, então a cozinha não gasta requisição com ele.
  useEffect(() => {
    if (!user) return;
    if (isAdmin || user.perfil === "NUTRICIONISTA") void prefetchar(`/api/dashboard/resumo?${new URLSearchParams({ inicio: diasAtrasISO(30), fim: hojeISO() })}`);
    if (isAdmin || isCozinha) {
      void prefetchar("/api/estoque");
      void prefetchar("/api/balancos");
      void prefetchar("/api/produtos-abertos?finalizado=false");
    }
  }, [user, isAdmin, isCozinha]);

  if (!user) return null;
  const perfil = user.perfil;
  const nav = buildNav(perfil);
  const showBell = isAdmin || isCozinha;

  return (
    <div className="shell">
      <button
        className="shell__scrim"
        aria-label="Fechar menu"
        onClick={() => setNavOpen(false)}
        tabIndex={navOpen ? 0 : -1}
      />
      <aside className={`sidebar ${navOpen ? "sidebar--open" : ""}`}>
        <Brand />
        <nav className="sidebar__nav" aria-label="Navegação principal">
          {nav.map((g) => (
            <div key={g.heading} className="navgroup">
              <p className="navgroup__heading">{g.heading}</p>
              <ul className="navgroup__list">
                {g.items.map((it) => (
                  <li key={it.to}>
                    <NavLink
                      to={it.to}
                      end={it.end}
                      onClick={() => setNavOpen(false)}
                      className={({ isActive }) =>
                        `navitem ${isActive ? "navitem--active" : ""} ${isActive && it.to === "/desperdicio" ? "navitem--active-danger" : ""}`
                      }
                    >
                      <Icon name={it.icon} size={18} />
                      <span>{it.label}</span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
        <div className="sidebar__user">
          <div className="sidebar__user-name">
            <span className="avatar" aria-hidden="true">
              {user.nome
                .split(" ")
                .slice(0, 2)
                .map((p) => p[0])
                .join("")
                .toUpperCase()}
            </span>
            <div className="sidebar__user-meta">
              <strong>{user.nome}</strong>
              <RoleLabel perfil={perfil} />
            </div>
          </div>
          <button className="sidebar__logout" onClick={() => void logout()} aria-label="Sair da conta">
            <Icon name="logout" size={18} />
          </button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button
            className="topbar__menu"
            onClick={() => setNavOpen((o) => !o)}
            aria-label={navOpen ? "Fechar menu" : "Abrir menu"}
            aria-expanded={navOpen}
          >
            <Icon name="menu" size={22} />
          </button>
          <div className="topbar__spacer" />
          {user.restauranteNome && (
            <p className="topbar__store" title={`Loja: ${user.restauranteNome}`}>
              <Icon name="store" size={16} />
              <span>{user.restauranteNome}</span>
            </p>
          )}
          {showBell && <AlertBell />}
        </header>
        <main className="main__content">
          <div className="main__inner">
            {falhaCache && <div className="alertbanner" role="status">
              Não foi possível atualizar algumas informações. Os dados já carregados continuam disponíveis.{" "}
              <Button size="sm" variant="ghost" onClick={tentarNovamenteCache}>Tentar novamente</Button>
            </div>}
            {children}
            <footer className="public-links"><Link to="/privacidade">Privacidade</Link></footer>
          </div>
        </main>
      </div>
    </div>
  );
}
