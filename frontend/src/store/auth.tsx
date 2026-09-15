import {
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
  canMove: boolean;
  canReport: boolean;
}

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUserDTO | null>(null);
  const [ready, setReady] = useState(false);
  const toast = useToast();

  const refetchMe = useCallback(async () => {
    try {
      setUser(await api.get<AuthenticatedUserDTO>("/api/auth/me"));
    } catch {
      setUser(null);
    } finally {
      setReady(true);
    }
  }, []);

  useEffect(() => {
    void refetchMe();
  }, [refetchMe]);

  useEffect(() => {
    const onUnauthorized = () => {
      setUser(null);
      resetCsrf();
    };
    window.addEventListener("estoq:unauthorized", onUnauthorized);
    return () => window.removeEventListener("estoq:unauthorized", onUnauthorized);
  }, []);

  const login = useCallback(
    async (email: string, senha: string) => {
      try {
        const u = await api.post<AuthenticatedUserDTO>("/api/auth/login", { email, senha });
        setUser(u);
        resetCsrf();
        toast.success(`Bem-vindo(a), ${u.nome.split(" ")[0]}`);
      } catch (e) {
        const err = e as { message?: string };
        toast.error("Não foi possível entrar", err.message);
        throw e;
      }
    },
    [toast]
  );

  const logout = useCallback(async () => {
    try {
      await api.post("/api/auth/logout");
    } catch {
      // sessão já pode ter expirado; segue o fluxo
    }
    resetCsrf();
    setUser(null);
  }, []);

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
      canMove: perfil === "ADMIN" || perfil === "COZINHA",
      canReport: perfil === "ADMIN" || perfil === "NUTRICIONISTA",
    };
  }, [user, ready, login, logout, refetchMe]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}