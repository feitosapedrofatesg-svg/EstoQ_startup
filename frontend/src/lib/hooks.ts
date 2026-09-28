import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api";

interface FetchState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  setData: (d: T | null) => void;
}

/** Cache SWR por caminho: páginas revisitadas abrem com os dados na hora. */
const cache = new Map<string, { data: unknown; ts: number }>();
const TTL_MS = 60_000;

export function useFetch<T>(path: string | null, deps: unknown[] = []): FetchState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(!!path);
  const [error, setError] = useState<string | null>(null);
  const mounted = useRef(true);
  const firstRun = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(
    async (force?: boolean) => {
      if (!path) {
        setData(null);
        setLoading(false);
        return;
      }
      const cached = cache.get(path) as { data: T; ts: number } | undefined;
      const fresca = cached !== undefined && Date.now() - cached.ts < TTL_MS;
      if (cached) {
        setData(cached.data);
        setLoading(false);
        if (fresca && !force) return;
      } else {
        setLoading(true);
      }
      setError(null);
      try {
        const result = await api.get<T>(path);
        cache.set(path, { data: result, ts: Date.now() });
        if (mounted.current) setData(result);
      } catch (e) {
        if (mounted.current) setError((e as Error).message);
      } finally {
        if (mounted.current) setLoading(false);
      }
    },
    [path]
  );

  useEffect(() => {
    // Primeira montagem usa o cache; troca de caminho (filtros) sempre refaz a rede.
    void run(!firstRun.current);
    firstRun.current = false;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, ...deps]);

  return { data, loading, error, refresh: () => run(true), setData };
}

/** Pré-carrega um caminho no cache SWR (usado pelo Layout ao montar). */
export async function prefetchar(path: string): Promise<void> {
  if (cache.has(path)) return;
  try {
    const data = await api.get<unknown>(path);
    cache.set(path, { data, ts: Date.now() });
  } catch {
    // sem rede ou sem permissão: deixa sem cache, a página busca normalmente
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