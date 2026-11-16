import {
  Fragment,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
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
  concluirLogin: (usuario: AuthenticatedUserDTO) => void;
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
  const consultaSessao = useRef<AbortController | null>(null);
  const geracaoAuth = useRef(0);
  const usuarioSessao = useRef<AuthenticatedUserDTO | null>(null);

  const cancelarConsulta = useCallback(() => {
    geracaoAuth.current++;
    consultaSessao.current?.abort();
    consultaSessao.current = null;
  }, []);

  const atualizarUsuario = useCallback((novo: AuthenticatedUserDTO | null) => {
    // Uma consulta inicial anônima não invalida formulários/requisições públicas já abertas.
    if (novo === null && usuarioSessao.current === null) return;
    usuarioSessao.current = novo;
    reiniciarCache(novo ? `${novo.restauranteId ?? 0}|${novo.id}|${novo.perfil}` : "anonimo");
    setUser(novo);
  }, []);

  const refetchMe = useCallback(async () => {
    cancelarConsulta();
    const geracao = geracaoAuth.current;
    const controller = new AbortController();
    consultaSessao.current = controller;
    const vigente = () => geracao === geracaoAuth.current && !controller.signal.aborted;
    try {
      const usuario = await api.get<AuthenticatedUserDTO>("/api/auth/me", {
        signal: controller.signal, notifyUnauthorized: false,
      });
      if (vigente()) atualizarUsuario(usuario);
    } catch {
      if (vigente()) atualizarUsuario(null);
    } finally {
      if (vigente()) {
        consultaSessao.current = null;
        setReady(true);
      }
    }
  }, [atualizarUsuario, cancelarConsulta]);

  useEffect(() => {
    void refetchMe();
    return cancelarConsulta;
  }, [refetchMe, cancelarConsulta]);

  useEffect(() => {
    const onUnauthorized = () => {
      cancelarConsulta();
      atualizarUsuario(null);
      setReady(true);
      resetCsrf();
    };
    window.addEventListener("estoq:unauthorized", onUnauthorized);
    return () => window.removeEventListener("estoq:unauthorized", onUnauthorized);
  }, [atualizarUsuario, cancelarConsulta]);

  const concluirLogin = useCallback((u: AuthenticatedUserDTO) => {
    cancelarConsulta();
    atualizarUsuario(u);
    setReady(true);
    resetCsrf();
    toast.success(`Bem-vindo(a), ${u.nome.split(" ")[0]}`);
  }, [toast, atualizarUsuario, cancelarConsulta]);

  const login = useCallback(
    async (email: string, senha: string) => {
      cancelarConsulta();
      try {
        const u = await api.post<AuthenticatedUserDTO>("/api/auth/login", { email, senha });
        concluirLogin(u);
      } catch (e) {
        const err = e as { message?: string };
        toast.error("Não foi possível entrar", err.message);
        throw e;
      } finally {
        setReady(true);
      }
    },
    [toast, concluirLogin, cancelarConsulta]
  );

  const logout = useCallback(async () => {
    cancelarConsulta();
    try {
      await api.post("/api/auth/logout");
    } catch {
      // sessão já pode ter expirado; segue o fluxo
    }
    resetCsrf();
    atualizarUsuario(null);
    setReady(true);
  }, [atualizarUsuario, cancelarConsulta]);

  const value = useMemo<AuthCtx>(() => {
    const perfil = user?.perfil;
    return {
      user,
      ready,
      login,
      concluirLogin,
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
  }, [user, ready, login, concluirLogin, logout, refetchMe]);

  return <Ctx.Provider value={value}><Fragment key={user ? `${user.id}|${user.restauranteId}|${user.perfil}` : "anonimo"}>{children}</Fragment></Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
