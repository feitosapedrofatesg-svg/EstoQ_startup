import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { NavLink, Link } from "react-router-dom";
import { useAuth } from "../store/auth";
import { api } from "../lib/api";
import type { AlertaDTO, Perfil } from "../lib/types";
import { Icon, type IconName } from "./Icon";
import { fmtDateTime } from "../lib/format";
import { useToast } from "../store/toast";

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
        items: [{ to: "/plataforma", label: "Restaurantes", icon: "store", end: true }],
      },
    ];
  }
  const groups: NavGroup[] = [
    {
      heading: "Operação",
      items: [
        { to: "/dashboard", label: "Visão geral", icon: "dashboard", end: true },
        { to: "/estoque", label: "Estoque", icon: "boxes" },
        { to: "/movimentacoes", label: "Movimentações", icon: "arrows" },
        { to: "/produtos-abertos", label: "Embalagens abertas", icon: "box-open" },
        { to: "/balanco", label: "Balanço físico", icon: "scale" },
      ],
    },
  ];
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
        { to: "/backup", label: "Backup", icon: "archive" },
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
        <img src="/logo_small.png" alt="" width={44} height={29} />
      </span>
      <span className="brand__word">
        esto<span className="brand__accent">Q</span>
      </span>
    </Link>
  );
}

function RoleLabel({ perfil }: { perfil: Perfil }) {
  const map: Record<Perfil, string> = {
    ADMIN: "Administrador",
    COZINHA: "Cozinha",
    NUTRICIONISTA: "Nutricionista",
    PLATAFORMA: "Plataforma",
  };
  return <span className="role-tag role-tag--plataforma">{map[perfil]}</span>;
}

function AlertBell() {
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [alerts, setAlerts] = useState<AlertaDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);
  const toast = useToast();

  const loadCount = useCallback(async () => {
    try {
      setCount(await api.get<number>("/api/alertas/abertos"));
    } catch {
      /* colapsa com 0 em falha de rede */
    }
  }, []);

  const openPanel = async () => {
    setOpen((o) => !o);
    if (!open) {
      setLoading(true);
      try {
        const list = await api.get<AlertaDTO[]>("/api/alertas");
        setAlerts(list);
      } catch {
        /* sem alertas */
      } finally {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    void loadCount();
    const t = window.setInterval(loadCount, 60_000);
    return () => window.clearInterval(t);
  }, [loadCount]);

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  const markSeen = async (id: number) => {
    try {
      await api.put(`/api/alertas/${id}/visualizado`);
      setAlerts((prev) => prev.filter((a) => a.id !== id));
      void loadCount();
    } catch (e) {
      toast.error("Não foi possível atualizar", (e as Error).message);
    }
  };

  return (
    <div className="bell" ref={boxRef}>
      <button
        className="bell__btn"
        onClick={openPanel}
        aria-label={`Alertas${count ? `, ${count} novos` : ""}`}
        aria-expanded={open}
      >
        <Icon name="bell" size={20} />
        {count > 0 && (
          <span className="bell__badge" aria-hidden="true">
            {count > 9 ? "9+" : count}
          </span>
        )}
      </button>
      {open && (
        <div className="bell__panel" role="dialog" aria-label="Central de alertas">
          <p className="bell__title">Central de alertas</p>
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
                  <button className="bell__seen" onClick={() => markSeen(a.id)}>
                    Marcar como visto
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
                        `navitem ${isActive ? "navitem--active" : ""}`
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
          {showBell && <AlertBell />}
        </header>
        <main className="main__content">
          <div className="main__inner">{children}</div>
        </main>
      </div>
    </div>
  );
}

