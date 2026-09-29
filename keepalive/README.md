# estoQ keep-alive (Cloudflare Workers)

Worker de cron que mantém o backend do Render (plano free) acordado.
O Render free dorme após ~15 min sem tráfego; chamar o `healthCheckPath`
(`/api/auth/csrf`) a cada 10 minutos reseta o timer de idle.

## Deploy (uma vez)

```bash
npm i -g wrangler
cd keepalive
wrangler login
wrangler deploy
```

O cron dispara sozinho após o deploy. Para testar manualmente, abra a URL
do worker no navegador (deve responder `ok`).

> Obs.: isso mantém o **Render** acordado. O banco **Neon** suspende por
> conta própria (~5 min de ociosidade); a primeira consulta real após esse
> tempo o acorda (~1–3s).