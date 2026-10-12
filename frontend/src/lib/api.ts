import { sessaoAtual } from "./sessao";
import type { ErrorResponse } from "./types";

export class ApiError extends Error {
  status: number;
  title?: string;
  motive?: string | null;

  constructor(status: number, message: string, title?: string, motive?: string | null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.title = title;
    this.motive = motive ?? null;
  }
}

const CSRF_PATH = "/api/auth/csrf";
const CSRF_ERROR = "Token CSRF inválido ou expirado.";

let csrfToken: string | null = null;
let csrfHeader = "X-CSRF-TOKEN";

export async function fetchCsrf(force = false): Promise<string> {
  const sessao = sessaoAtual();
  if (csrfToken && !force) return csrfToken;
  const res = await fetch(CSRF_PATH, { credentials: "include" });
  if (!res.ok) throw new ApiError(res.status, "Não foi possível iniciar a sessão segura.");
  const data = (await res.json()) as { token: string; headerName: string };
  conferirSessao(sessao);
  csrfToken = data.token;
  csrfHeader = data.headerName;
  return csrfToken;
}

export function resetCsrf(): void {
  csrfToken = null;
}

function conferirSessao(sessao: string): void {
  if (sessao !== sessaoAtual()) throw new ApiError(409, "Sua sessão mudou. Repita a operação na conta atual.");
}

function friendlyStatus(status: number): string {
  if (status === 401) return "Sua sessão expirou. Entre novamente.";
  if (status === 403) return "Operação não permitida para o seu perfil.";
  if (status === 404) return "Registro não encontrado.";
  if (status === 409) return "Esse registro mudou desde a última leitura. Recarregue e tente de novo.";
  if (status === 429) return "Muitas tentativas. Aguarde alguns minutos e tente de novo.";
  return "Erro inesperado. Tente novamente.";
}

async function parseError(res: Response): Promise<ApiError> {
  let body: ErrorResponse | null = null;
  try {
    body = (await res.json()) as ErrorResponse;
  } catch {
    body = null;
  }
  const message = body?.message || body?.title || friendlyStatus(res.status);
  return new ApiError(res.status, message, body?.title, body?.motive);
}

async function isCsrfFailure(res: Response): Promise<boolean> {
  if (res.status !== 403) return false;
  try {
    const body = (await res.clone().json()) as ErrorResponse;
    return body.message === CSRF_ERROR;
  } catch {
    return false;
  }
}

type Method = "GET" | "POST" | "PUT" | "DELETE";

async function request<T>(
  method: Method,
  path: string,
  body?: unknown,
  retried = false
): Promise<T> {
  const sessao = sessaoAtual();
  const headers: Record<string, string> = { Accept: "application/json" };

  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body; // O navegador inclui o boundary do multipart.
  } else if (body !== undefined) {
    payload = JSON.stringify(body);
    headers["Content-Type"] = "application/json";
  }

  if (method !== "GET") {
    try {
      const token = await fetchCsrf();
      headers[csrfHeader] = token;
    } catch (e) {
      throw e;
    }
  }

  conferirSessao(sessao);
  let res: Response;
  try {
    res = await fetch(path, {
      method,
      headers,
      credentials: "include",
      body: payload,
    });
  } catch {
    throw new ApiError(0, "Não foi possível falar com o servidor. Verifique sua conexão.");
  }

  conferirSessao(sessao);
  if (!retried && method !== "GET" && await isCsrfFailure(res)) {
    await fetchCsrf(true);
    conferirSessao(sessao);
    return request<T>(method, path, body, true);
  }

  if (!res.ok) {
    if (sessao === sessaoAtual() && res.status === 401 && !path.startsWith("/api/auth/login")) {
      window.dispatchEvent(new CustomEvent("estoq:unauthorized"));
    }
    throw await parseError(res);
  }

  if (res.status === 204) return undefined as T;
  const contentType = res.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    const resultado = (await res.json()) as T;
    conferirSessao(sessao);
    return resultado;
  }
  return (await res.text()) as unknown as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  del: <T>(path: string) => request<T>("DELETE", path),

  async blob(path: string): Promise<Blob> {
    const sessao = sessaoAtual();
    const res = await fetch(path, { credentials: "include", headers: { Accept: "application/pdf" } });
    conferirSessao(sessao);
    if (res.status === 401) window.dispatchEvent(new CustomEvent("estoq:unauthorized"));
    if (!res.ok) throw await parseError(res);
    const blob = await res.blob();
    conferirSessao(sessao);
    return blob;
  },

  /** POST que devolve um arquivo (bytes) como resposta — usado p/ baixar o backup na hora. */
  async blobPost(path: string): Promise<{ blob: Blob; filename: string }> {
    const sessao = sessaoAtual();
    const headers: Record<string, string> = { Accept: "application/octet-stream" };
    headers[csrfHeader] = await fetchCsrf();
    conferirSessao(sessao);
    let res: Response;
    try {
      res = await fetch(path, { method: "POST", headers, credentials: "include" });
    } catch {
      throw new ApiError(0, "Não foi possível falar com o servidor. Verifique sua conexão.");
    }
    conferirSessao(sessao);
    if (await isCsrfFailure(res)) {
      headers[csrfHeader] = await fetchCsrf(true);
      conferirSessao(sessao);
      res = await fetch(path, { method: "POST", headers, credentials: "include" });
    }
    conferirSessao(sessao);
    if (!res.ok) {
      if (res.status === 401) window.dispatchEvent(new CustomEvent("estoq:unauthorized"));
      throw await parseError(res);
    }
    const blob = await res.blob();
    conferirSessao(sessao);
    const cd = res.headers.get("content-disposition") ?? "";
    const match = /filename="?([^";]+)"?/.exec(cd);
    const fallback = `estoq-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}.dump`;
    return { blob, filename: match?.[1] ?? fallback };
  },
};

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}