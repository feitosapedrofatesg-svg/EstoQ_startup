# estoQ keep-alive (Cloudflare Workers)

Worker Cloudflare que pode consultar a prontidão do backend de produção pelo
endpoint `/api/saude/pronto` (público e sem criar sessão). O agendamento
automático está desativado na configuração versionada em `wrangler.toml`
(`crons = []`). Para aplicar a mudança ao Worker já publicado, faça deploy da
configuração com `wrangler deploy`; o estado publicado não foi verificado aqui.

## Deploy (uma vez)

```bash
npm i -g wrangler
cd keepalive
wrangler login
wrangler deploy
```

Para fazer uma consulta manual, abra a URL do worker no navegador. Ele tenta
consultar o backend e responde `ok`. Essa chamada manual não configura um cron.
