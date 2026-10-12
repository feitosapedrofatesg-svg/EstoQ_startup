import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type SetStateAction,
} from "react";
import { api } from "./api";
import type { Page } from "./types";
import { mudarSessao, sessaoAtual } from "./sessao";

import { assinarCache, buscarCache, chaveCache, gravarCache, intervaloCache, lerCache, limparCache } from "./cache";

export { invalidarCache } from "./cache";

interface FetchState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  setData: Dispatch<SetStateAction<T | null>>;
}


export function reiniciarCache(identidade = "anonimo"): void {
  mudarSessao(identidade);
  limparCache();
}

/** Reutiliza listas por sessão. O terceiro argumento legado é mantido para compatibilidade;
 * a atualização na montagem agora segue o prazo de cada consulta. */
export function useFetch<T>(path: string | null, deps: unknown[] = [], _revalidateOnMount = false, carregar?: (path: string) => Promise<T>): FetchState<T> {
  const chave = path ? chaveCache(carregar ? `${path}#todas` : path) : null;
  const inicial = (): { chave: string | null; data: T | null; error: string | null } => ({ chave, data: chave ? (lerCache(chave)?.data as T ?? null) : null, error: null });
  const [estado, setEstado] = useState(inicial);
  const mounted = useRef(false);
  const atual = useRef(chave);
  atual.current = chave;
  const loader = useRef(carregar);
  const sequencia = useRef(0);
  const ultimasDeps = useRef(deps);
  loader.current = carregar;
  const visivel = estado.chave === chave ? estado : inicial();

  const run = useCallback(async (force = false) => {
    if (!path || !chave) return;
    const entrada = lerCache(chave);
    if (!force && entrada && Date.now() - entrada.ts < intervaloCache(path)) return;
    const numero = ++sequencia.current;
    const sessao = sessaoAtual();
    const vigente = () => mounted.current && atual.current === chave && sessao === sessaoAtual() && numero === sequencia.current;
    if (vigente()) setEstado(prev => ({ chave, data: prev.chave === chave ? prev.data : (entrada?.data as T ?? null), error: null }));
    try {
      const resultado = await buscarCache(chave, () => loader.current ? loader.current(path) : api.get<T>(path));
      if (vigente() && resultado !== undefined) setEstado({ chave, data: resultado, error: null });
    } catch (e) {
      if (vigente()) setEstado(prev => ({ ...prev, error: (e as Error).message }));
    }
  }, [path, chave]);

  useEffect(() => {
    mounted.current = true;
    setEstado(inicial());
    const intervalo = path ? intervaloCache(path) : 60_000;
    let timer: number;
    let encerrado = false;
    let ultimaTentativa = 0;
    let ultimaResposta = 0;
    const agendar = () => {
      window.clearTimeout(timer);
      if (encerrado || !chave) return;
      const base = Math.max(lerCache(chave)?.ts ?? 0, ultimaTentativa, ultimaResposta) || Date.now();
      timer = window.setTimeout(() => void verificar(), Math.max(1, base + intervalo - Date.now()));
    };
    const verificar = async () => {
      if (document.visibilityState === "visible" && navigator.onLine) {
        const entrada = chave ? lerCache(chave) : undefined;
        if (!entrada || Date.now() - entrada.ts >= intervalo) ultimaTentativa = Date.now();
        await run();
      } else ultimaTentativa = Date.now();
      agendar();
    };
    const desassinar = chave ? assinarCache(chave, evento => {
      if (evento.tipo === "invalidado") {
        if (document.visibilityState === "visible" && navigator.onLine) {
          ultimaTentativa = Date.now();
          void run(true).finally(agendar);
        }
        return;
      }
      ultimaResposta = Date.now();
      setEstado({ chave, data: evento.data as T, error: null });
      agendar();
    }) : () => {};
    const mudouDependencia = deps.length !== ultimasDeps.current.length || deps.some((valor, i) => !Object.is(valor, ultimasDeps.current[i]));
    ultimasDeps.current = deps;
    const entrada = chave ? lerCache(chave) : undefined;
    if (!entrada || Date.now() - entrada.ts >= intervalo || mudouDependencia) ultimaTentativa = Date.now();
    void run(mudouDependencia).finally(agendar);
    // O prazo começa na última resposta, evitando adiar uma atualização por mais um ciclo.
    agendar();
    document.addEventListener("visibilitychange", verificar);
    window.addEventListener("focus", verificar);
    window.addEventListener("online", verificar);
    return () => {
      mounted.current = false;
      encerrado = true;
      sequencia.current++;
      desassinar(); window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", verificar);
      window.removeEventListener("focus", verificar);
      window.removeEventListener("online", verificar);
    };
    // As dependências explícitas mantêm compatibilidade com os consumidores atuais.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave, ...deps]);

  const setData: Dispatch<SetStateAction<T | null>> = valor => {
    setEstado(prev => ({ chave, data: typeof valor === "function" ? (valor as (data: T | null) => T | null)(prev.chave === chave ? prev.data : null) : valor, error: null }));
  };
  return { data: visivel.data, loading: !!path && visivel.data === null && !visivel.error,
    error: visivel.error, refresh: () => run(true), setData };
}

/** O pré-carregamento compartilha a mesma requisição da tela, sem buscar caminhos vencidos duas vezes. */
export async function prefetchar(path: string): Promise<void> {
  const chave = chaveCache(path);
  const entrada = lerCache(chave);
  if (entrada && Date.now() - entrada.ts < intervaloCache(path)) return;
  try { await buscarCache(chave, () => api.get<unknown>(path)); } catch { /* A tela pode tentar novamente. */ }
}

export function updateCache<T>(path: string, updater: (prev: T) => T): void {
  const chave = chaveCache(path);
  const entrada = lerCache(chave);
  if (entrada) gravarCache(chave, updater(entrada.data as T));
}

export function useDebounced<T>(value: T, ms = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(value), ms);
    return () => window.clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

/** Seletores percorrem todas as páginas, sem aumentar o limite seguro do servidor. */
export async function carregarPaginas<T>(path: string): Promise<T[]> {
  const lista: T[] = [];
  const url = new URL(path, window.location.origin);
  const sessao = sessaoAtual();
  url.searchParams.set("size", "100");
  for (let pagina = 0; ; pagina++) {
    url.searchParams.set("page", String(pagina));
    const resultado = await api.get<Page<T>>(url.pathname + url.search);
    if (sessao !== sessaoAtual()) throw new Error("Sessão alterada durante o carregamento.");
    lista.push(...resultado.content);
    if (pagina + 1 >= resultado.totalPages) return lista;
  }
}

export function useTodasPaginas<T>(path: string): FetchState<T[]> {
  return useFetch<T[]>(path, [], false, carregarPaginas<T>);
}
