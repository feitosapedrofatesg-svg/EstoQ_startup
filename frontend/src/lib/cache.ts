import { sessaoAtual } from "./sessao";

type Entrada = { data: unknown; ts: number; bytes: number };
type Evento = { tipo: "dados"; data: unknown } | { tipo: "invalidado" };
const entradas = new Map<string, Entrada>();
const ouvintes = new Map<string, Set<(evento: Evento) => void>>();
const pendentes = new Map<string, Promise<unknown>>();
const falhas = new Set<string>();
const avisos = new Set<() => void>();
const MAX_ENTRADAS = 200;
const MAX_BYTES = 20 * 1024 * 1024;
let bytes = 0;

function avisarFalhas(): void { for (const aviso of avisos) aviso(); }
export function possuiFalhasCache(): boolean { return [...falhas].some(chave => ouvintes.has(chave)); }
export function assinarFalhasCache(aviso: () => void): () => void {
  avisos.add(aviso); return () => { avisos.delete(aviso); };
}
export function tentarNovamenteCache(): void {
  for (const chave of [...falhas]) if (ouvintes.has(chave)) avisar(chave, { tipo: "invalidado" });
}

export function intervaloCache(path: string): number {
  const caminho = path.split(/[?#]/)[0];
  return /^\/api\/(estoque|lotes|consumos|desperdicios|produtos-abertos|movimentacoes|balancos|alertas)(\/|$)/.test(caminho)
    || /^\/api\/produtos\/[^/]+\/(saldo|lotes-disponiveis)$/.test(caminho) ? 15_000 : 60_000;
}

export function chaveCache(path: string): string { return `${sessaoAtual()}::${path}`; }

/** A ordem do Map acompanha o último uso, permitindo limitar a memória por LRU. */
export function lerCache(chave: string): Entrada | undefined {
  const entrada = entradas.get(chave);
  if (entrada) { entradas.delete(chave); entradas.set(chave, entrada); }
  return entrada;
}

function avisar(chave: string, evento: Evento): void {
  for (const ouvir of ouvintes.get(chave) ?? []) ouvir(evento);
}

function remover(chave: string): void {
  bytes -= entradas.get(chave)?.bytes ?? 0;
  entradas.delete(chave);
}

export function gravarCache(chave: string, data: unknown): void {
  remover(chave);
  const tamanho = new TextEncoder().encode(JSON.stringify(data) ?? "").byteLength;
  if (tamanho <= MAX_BYTES) {
    entradas.set(chave, { data, ts: Date.now(), bytes: tamanho });
    bytes += tamanho;
    while (entradas.size > MAX_ENTRADAS || bytes > MAX_BYTES) remover(entradas.keys().next().value!);
  }
  // Respostas grandes continuam disponíveis aos componentes abertos, sem retenção.
  avisar(chave, { tipo: "dados", data });
}

export function assinarCache(chave: string, ouvir: (evento: Evento) => void): () => void {
  const lista = ouvintes.get(chave) ?? new Set();
  lista.add(ouvir); ouvintes.set(chave, lista);
  return () => {
    lista.delete(ouvir);
    if (!lista.size) { ouvintes.delete(chave); falhas.delete(chave); avisarFalhas(); }
  };
}

/** Uma promessa por consulta. Invalidação ou troca de sessão descarta a resposta antiga. */
export function buscarCache<T>(chave: string, carregar: () => Promise<T>): Promise<T | undefined> {
  const existente = pendentes.get(chave);
  if (existente) return existente as Promise<T | undefined>;
  const sessao = sessaoAtual();
  falhas.delete(chave); avisarFalhas();
  const promessa = Promise.resolve().then(carregar).then(data => {
    if (sessao !== sessaoAtual() || pendentes.get(chave) !== promessa) return undefined;
    gravarCache(chave, data);
    return data;
  }).catch(erro => {
    if (sessao !== sessaoAtual() || pendentes.get(chave) !== promessa) return undefined;
    if (ouvintes.has(chave)) { falhas.add(chave); avisarFalhas(); }
    throw erro;
  }).finally(() => { if (pendentes.get(chave) === promessa) pendentes.delete(chave); });
  pendentes.set(chave, promessa);
  return promessa;
}

export function limparCache(): void {
  entradas.clear(); pendentes.clear(); ouvintes.clear(); falhas.clear(); bytes = 0;
  avisarFalhas();
}

/** Mantém dados visíveis, mas os marca como vencidos e cancela a publicação de buscas antigas. */
export function invalidarCache(prefixos: string[]): void {
  const inicio = `${sessaoAtual()}::`;
  const chaves = new Set([...entradas.keys(), ...pendentes.keys(), ...ouvintes.keys()]);
  for (const chave of chaves) {
    if (!prefixos.some(prefixo => chave.startsWith(inicio + prefixo))) continue;
    const entrada = entradas.get(chave);
    if (entrada) entrada.ts = 0;
    pendentes.delete(chave);
    avisar(chave, { tipo: "invalidado" });
  }
}

const OPERACAO = ["/api/estoque", "/api/lotes", "/api/produtos", "/api/produtos-abertos", "/api/movimentacoes",
  "/api/consumos", "/api/desperdicios", "/api/balancos", "/api/alertas", "/api/dashboard", "/api/relatorios"];

/** Regras por operação: qualquer gravação desconhecida vence o cache inteiro da sessão. */
export function invalidarAposGravacao(path: string): void {
  const raiz = path.split(/[?#]/)[0].split("/")[2];
  if (raiz === "auth" || raiz === "registro" || (raiz === "notas-fiscais" && !path.includes("/confirmar"))) return;
  if (["entradas", "consumos", "desperdicios", "produtos-abertos", "movimentacoes", "lotes", "balancos"].includes(raiz)) invalidarCache(OPERACAO);
  else if (raiz === "notas-fiscais") invalidarCache([...OPERACAO, "/api/categorias"]);
  else if (["produtos", "categorias", "parametros-estoque"].includes(raiz)) invalidarCache([...OPERACAO, "/api/categorias", "/api/parametros-estoque"]);
  else if (raiz === "usuarios") invalidarCache(["/api/usuarios"]);
  else if (raiz === "alertas") invalidarCache(["/api/alertas"]);
  else if (raiz === "configuracoes-balanco") invalidarCache(["/api/configuracoes-balanco", "/api/balancos", "/api/alertas", "/api/dashboard"]);
  else if (["parametros-cmv", "relatorios"].includes(raiz)) invalidarCache(["/api/parametros-cmv", "/api/relatorios", "/api/dashboard"]);
  else if (raiz === "backups") invalidarCache(["/api/backups"]);
  else invalidarCache(["/api/"]);
}
