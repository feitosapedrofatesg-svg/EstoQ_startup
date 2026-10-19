# estoQ keep-alive (Cloudflare Workers)

Worker de cron que mantém o backend do Render (plano free) acordado e o
banco Supabase free ativo. O Render free dorme após ~15 min sem tráfego e
projetos Supabase free pausam após 7 dias sem atividade; chamar o
`/api/saude/pronto` (verificação de prontidão, pública, sem criar sessão)
a cada 10 minutos mantém os dois despertos.

## Deploy (uma vez)

```bash
npm i -g wrangler
cd keepalive
wrangler login
wrangler deploy
```

O cron dispara sozinho após o deploy. Para testar manualmente, abra a URL
do worker no navegador (deve responder `ok`).