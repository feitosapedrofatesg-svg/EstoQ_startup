import {
  Fragment,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, resetCsrf } from "../lib/api";
import type { AuthenticatedUserDTO } from "../lib/types";
import { reiniciarCache } from "../lib/hooks";
import { useToast } from "./toast";

interface AuthCtx {
  user: AuthenticatedUserDTO | null;
  ready: boolean;
  login: (email: string, senha: string) => Promise<void>;
  logout: () => Promise<void>;
  refetchMe: () => Promise<void>;
  isAdmin: boolean;
  isCozinha: boolean;
  isNutri: boolean;
  isPlataforma: boolean;
  canMove: boolean;
  canReport: boolean;
  // Leitura de estoque e catálogo: liberada a ADMIN e COZINHA, negada ao
  // NUTRICIONISTA, que só tem visão geral e relatórios. Espelha as regras
  // de GET em SecurityConfig.
  canSeeEstoque: boolean;
  canSeeCatalogo: boolean;
}

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUserDTO | null>(null);
  const [ready, setReady] = useState(false);
  const toast = useToast();

  const atualizarUsuario = useCallback((novo: AuthenticatedUserDTO | null) => {
    reiniciarCache(novo ? `${novo.restauranteId ?? 0}|${novo.id}|${novo.perfil}` : "anonimo");
    setUser(novo);
  }, []);

  const refetchMe = useCallback(async () => {
    try {
      atualizarUsuario(await api.get<AuthenticatedUserDTO>("/api/auth/me"));
    } catch {
      atualizarUsuario(null);
    } finally {
      setReady(true);
    }
  }, [atualizarUsuario]);

  useEffect(() => {
    void refetchMe();
  }, [refetchMe]);

  useEffect(() => {
    const onUnauthorized = () => {
      atualizarUsuario(null);
      resetCsrf();
    };
    window.addEventListener("estoq:unauthorized", onUnauthorized);
    return () => window.removeEventListener("estoq:unauthorized", onUnauthorized);
  }, [atualizarUsuario]);

  const login = useCallback(
    async (email: string, senha: string) => {
      try {
        const u = await api.post<AuthenticatedUserDTO>("/api/auth/login", { email, senha });
        atualizarUsuario(u);
        resetCsrf();
        toast.success(`Bem-vindo(a), ${u.nome.split(" ")[0]}`);
      } catch (e) {
        const err = e as { message?: string };
        toast.error("Não foi possível entrar", err.message);
        throw e;
      }
    },
    [toast, atualizarUsuario]
  );

  const logout = useCallback(async () => {
    try {
      await api.post("/api/auth/logout");
    } catch {
      // sessão já pode ter expirado; segue o fluxo
    }
    resetCsrf();
    atualizarUsuario(null);
  }, [atualizarUsuario]);

  const value = useMemo<AuthCtx>(() => {
    const perfil = user?.perfil;
    return {
      user,
      ready,
      login,
      logout,
      refetchMe,
      isAdmin: perfil === "ADMIN",
      isCozinha: perfil === "COZINHA",
      isNutri: perfil === "NUTRICIONISTA",
      isPlataforma: perfil === "PLATAFORMA",
      canMove: perfil === "ADMIN" || perfil === "COZINHA",
      canReport: perfil === "ADMIN" || perfil === "NUTRICIONISTA",
      canSeeEstoque: perfil === "ADMIN" || perfil === "COZINHA",
      canSeeCatalogo: perfil === "ADMIN" || perfil === "COZINHA",
    };
  }, [user, ready, login, logout, refetchMe]);

  return <Ctx.Provider value={value}><Fragment key={user ? `${user.id}|${user.restauranteId}|${user.perfil}` : "anonimo"}>{children}</Fragment></Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}