// Keep-alive do estoQ: impede o Render free de dormir (sleep após ~15 min
// sem tráfego) e mantém o banco Supabase free ativo (projetos free pausam
// após 7 dias sem atividade). Usa a verificação de prontidão, que é pública
// e sem efeito colateral (não cria sessão).
//
// Alvo anterior (Oregon, desativado em 18/10/2026): estoq-backend-jv04.onrender.com
const ALVO = "https://estoq-backend-va.onrender.com/api/saude/pronto";

export default {
  // Disparado pelo cron do wrangler.toml a cada 10 minutos.
  async scheduled(_event, _env, ctx) {
    ctx.waitUntil(ping());
  },

  // Permite testar na hora acessando a URL do worker no navegador.
  async fetch() {
    await ping();
    return new Response("ok");
  },
};

async function ping() {
  try {
    await fetch(ALVO, { headers: { "User-Agent": "estoq-keepalive" } });
  } catch {
    // Render fora do ar: o próximo cron tenta de novo.
  }
}
