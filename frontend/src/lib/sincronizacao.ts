import { invalidarCache } from "./cache";
import { api } from "./api";
import { sessaoAtual } from "./sessao";

const PREFIXOS: Record<string, string[]> = {
  BALANCOS: ["/api/balancos", "/api/alertas", "/api/dashboard"],
  ESTOQUE: ["/api/estoque", "/api/lotes", "/api/produtos", "/api/produtos-abertos", "/api/movimentacoes", "/api/consumos", "/api/desperdicios", "/api/alertas", "/api/dashboard", "/api/relatorios"],
  CATALOGO: ["/api/categorias", "/api/produtos", "/api/parametros-estoque", "/api/estoque", "/api/lotes", "/api/dashboard", "/api/relatorios"],
  USUARIOS: ["/api/usuarios"],
  CONFIGURACOES_BALANCO: ["/api/configuracoes-balanco", "/api/balancos", "/api/alertas", "/api/dashboard"],
  ALERTAS: ["/api/alertas"],
  CMV: ["/api/parametros-cmv", "/api/relatorios", "/api/dashboard"],
};

/** Uma conexão por aba. Os intervalos normais continuam recuperando avisos perdidos. */
export function iniciarSincronizacao(): () => void {
  const sessao = sessaoAtual();
  let encerrado = false;
  let fonte: EventSource | null = null;
  let reconexao: number | undefined;
  let agrupamento: number | undefined;
  let ultimaValidacao = 0;
  const prefixos = new Set<string>();
  const vigente = () => !encerrado && sessao === sessaoAtual();
  const fechar = () => { fonte?.close(); fonte = null; window.clearTimeout(reconexao); };
  const agrupar = (caminhos: string[]) => {
    caminhos.forEach(caminho => prefixos.add(caminho));
    if (agrupamento !== undefined) return;
    agrupamento = window.setTimeout(() => {
      agrupamento = undefined;
      if (vigente()) invalidarCache([...prefixos]);
      prefixos.clear();
    }, 250);
  };
  const abrir = () => {
    if (!vigente() || fonte || document.visibilityState !== "visible" || !navigator.onLine) return;
    const conexao = new EventSource("/api/sincronizacao/eventos");
    fonte = conexao;
    const atual = () => vigente() && fonte === conexao;
    // Revalidar na conexão também recupera alterações feitas enquanto a aba estava fechada.
    conexao.addEventListener("conectado", () => { if (atual()) agrupar(["/api/"]); });
    conexao.addEventListener("alteracao", evento => {
      if (!atual()) return;
      try {
        const dados = JSON.parse((evento as MessageEvent).data) as { grupos?: unknown };
        if (Array.isArray(dados.grupos)) for (const grupo of dados.grupos)
          if (typeof grupo === "string" && Object.hasOwn(PREFIXOS, grupo)) agrupar(PREFIXOS[grupo]);
      } catch { /* Um aviso inválido não interfere nos dados nem no polling. */ }
    });
    conexao.onerror = () => {
      if (!atual()) return;
      fechar();
      // EventSource não expõe HTTP 401: a consulta normal aplica a expiração da sessão.
      if (Date.now() - ultimaValidacao >= 15_000) {
        ultimaValidacao = Date.now();
        void api.get("/api/auth/me").catch(() => {});
      }
      reconexao = window.setTimeout(abrir, 3_000);
    };
  };
  const visibilidade = () => {
    if (document.visibilityState === "visible" && navigator.onLine) abrir(); else fechar();
  };
  document.addEventListener("visibilitychange", visibilidade);
  window.addEventListener("online", visibilidade);
  window.addEventListener("offline", visibilidade);
  abrir();
  return () => {
    encerrado = true; fechar(); window.clearTimeout(agrupamento); prefixos.clear();
    document.removeEventListener("visibilitychange", visibilidade);
    window.removeEventListener("online", visibilidade);
    window.removeEventListener("offline", visibilidade);
  };
}
