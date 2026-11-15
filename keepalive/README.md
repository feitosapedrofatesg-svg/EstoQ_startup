# estoQ keep-alive (Cloudflare Workers)

Worker Cloudflare que consulta a prontidão do backend de produção pelo
endpoint `/api/saude/pronto` (público e sem criar sessão). O agendamento
automático está **desativado** na configuração versionada em `wrangler.toml`
(`crons = []`); para reativar, configure o cron e faça `wrangler deploy`. Como
`/pronto` consulta o banco, um cron ativo mantém backend e banco acordados e
aumenta o consumo de compute enquanto estiver ligado. O Worker publicado é
`https://estoq-keepalive.feitosapedrofatesg.workers.dev`.

## Deploy

```bash
npm i -g wrangler
cd keepalive
wrangler login
wrangler deploy
```

Se a máquina tiver mais de uma conta Cloudflare autenticada, informe a conta
correta para evitar publicar no lugar errado:

```bash
CLOUDFLARE_ACCOUNT_ID=<account-id> wrangler deploy
```

Para fazer uma consulta manual, abra a URL do worker no navegador. Ele tenta
consultar o backend e responde `ok`. Essa chamada manual não altera o cron.
