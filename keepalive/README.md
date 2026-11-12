# estoQ keep-alive (Cloudflare Workers)

Worker Cloudflare que consulta a prontidão do backend de produção pelo
endpoint `/api/saude/pronto` (público e sem criar sessão). O agendamento está
configurado para cada 10 minutos. Como `/pronto` consulta o banco, o cron pode
mantê-lo ativo e aumentar o consumo de compute enquanto estiver ligado. Para
aplicar a configuração ao Worker publicado, faça deploy com `wrangler deploy`.

## Deploy (uma vez)

```bash
npm i -g wrangler
cd keepalive
wrangler login
wrangler deploy
```

Para fazer uma consulta manual, abra a URL do worker no navegador. Ele tenta
consultar o backend e responde `ok`. Essa chamada manual não configura um cron.
