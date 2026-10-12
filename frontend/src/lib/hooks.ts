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

interface FetchState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  setData: Dispatch<SetStateAction<T | null>>;
}

/** Cache SWR por caminho: páginas revisitadas abrem com os dados na hora. */
const cache = new Map<string, { data: unknown; ts: number }>();
const TTL_MS = 60_000;

export function reiniciarCache(identidade = "anonimo"): void {
  mudarSessao(identidade);
  cache.clear();
}

function chaveCache(path: string): string {
  return `${sessaoAtual()}::${path}`;
}

export function useFetch<T>(path: string | null, deps: unknown[] = [], revalidateOnMount = false, carregar?: (path: string) => Promise<T>): FetchState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(!!path);
  const [error, setError] = useState<string | null>(null);
  const mounted = useRef(true);
  const firstRun = useRef(true);
  const requestSequence = useRef(0);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(
    async (force?: boolean) => {
      const sequence = ++requestSequence.current;
      const sessao = sessaoAtual();
      const vigente = () => mounted.current && sequence === requestSequence.current && sessao === sessaoAtual();
      if (!path) {
        setData(null);
        setLoading(false);
        return;
      }
      const caminhoCache = carregar ? `${path}#todas` : path;
      const cached = cache.get(chaveCache(caminhoCache)) as { data: T; ts: number } | undefined;
      const fresca = cached !== undefined && Date.now() - cached.ts < TTL_MS;
      if (cached) {
        setData(cached.data);
        setLoading(false);
        if (fresca && !force) return;
      } else {
        setData(null);
        setLoading(true);
      }
      setError(null);
      try {
        const result = await (carregar ? carregar(path) : api.get<T>(path));
        if (sessao !== sessaoAtual()) return;
        cache.set(chaveCache(caminhoCache), { data: result, ts: Date.now() });
        if (vigente()) setData(result);
      } catch (e) {
        if (vigente()) {
          setError((e as Error).message);
        }
      } finally {
        if (vigente()) setLoading(false);
      }
    },
    [path]
  );

  useEffect(() => {
    // Primeira montagem usa o cache; troca de caminho (filtros) sempre refaz a rede.
    void run(revalidateOnMount || !firstRun.current);
    firstRun.current = false;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, ...deps]);

  return { data, loading, error, refresh: () => run(true), setData };
}

/** Pré-carrega um caminho no cache SWR (usado pelo Layout ao montar). */
export async function prefetchar(path: string): Promise<void> {
  const sessao = sessaoAtual();
  if (cache.has(chaveCache(path))) return;
  try {
    const data = await api.get<unknown>(path);
    if (sessao === sessaoAtual()) cache.set(chaveCache(path), { data, ts: Date.now() });
  } catch {
    // sem rede ou sem permissão: deixa sem cache, a página busca normalmente
  }
}

/** Atualiza o cache SWR após uma gravação (ex.: mesclar um balanço respondido). */
export function updateCache<T>(path: string, updater: (prev: T) => T): void {
  const entry = cache.get(chaveCache(path));
  if (entry) {
    cache.set(chaveCache(path), { data: updater(entry.data as T), ts: Date.now() });
  }
}

export function useDebounced<T>(value: T, ms = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(value), ms);
    return () => window.clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

/** Invalida telas afetadas por uma entrada em lote, para que consultem os novos saldos. */
export function invalidarCache(prefixos: string[]): void {
  for (const caminho of cache.keys()) {
    if (prefixos.some(prefixo => caminho.startsWith(`${sessaoAtual()}::${prefixo}`))) cache.delete(caminho);
  }
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
