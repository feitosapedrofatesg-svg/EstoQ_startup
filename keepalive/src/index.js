// Keep-alive do estoQ: impede o Render free de dormir (sleep após ~15 min
// sem tráfego). Chama o healthCheckPath do serviço — que é público e sem
// efeito colateral (só gera CSRF em memória, não consulta o banco).
const ALVO = "https://estoq-backend-jv04.onrender.com/api/auth/csrf";

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