# Neon: consumo em ociosidade e indisponibilidade

Revisão: 18/10/2026. Ajuste para o piloto de baixo custo; não recupera a cota já consumida.

## Verificação automática sem consultar o banco

O `render.yaml` passa a usar `/api/saude/vivo`: confirma apenas o processo HTTP,
sem criar sessão ou abrir conexão. O worker de keepalive também usa essa rota.
O endpoint `/api/saude/pronto` continua disponível para diagnóstico manual e retorna
503 quando não consegue validar uma conexão. Não usá-lo em monitoramento periódico
no plano serverless de baixo custo: cada chamada pode manter o banco ativo.

**Configuração do serviço existente:** no Render, abrir o backend em Settings →
Health Checks e conferir que o caminho efetivo é `/api/saude/vivo`. Um commit no
Blueprint não comprova que um serviço criado manualmente recebeu a alteração.
Conferir também monitores externos; eles devem usar a rota `vivo`.

## Conexões de produção

O perfil `prod` usa Hikari com mínimo ocioso zero, descarte após 60 segundos sem
uso e keepalive JDBC desativado. O limite para obter conexão é 15 segundos e a
validação é de até três segundos. O limite máximo do pool permanece inalterado.
Não alterar a URL, o TLS ou as credenciais do banco para aplicar esse ajuste.

Uma aplicação ociosa não deve continuar enviando pings ao Neon. A primeira operação
após a suspensão pode demorar enquanto o banco acorda. Abas autenticadas abertas
continuam gerando consultas e SSE para validar sessão: fechar essas abas ao encerrar
o uso. Não remover a validação de sessão para economizar consultas.

## Mensagens de acesso

Falha interna do serviço de autenticação (incluindo consulta ao banco encapsulada
pelo Spring Security) retorna HTTP 503 usando `ErrorResponse` do core, sem detalhes
de conexão. Credencial recusada ou conta inativa continua 401 e excesso de tentativas
continua 429. O login distingue esses casos e mostra indisponibilidade também nos
erros 502 e 504 do gateway. Não trocar a senha por causa de uma indisponibilidade.

## Cota esgotada e publicação

Consultar no Neon o limite contratado, o período de faturamento e a data de
renovação. O intervalo mostrado no gráfico de métricas não prova a data de reset.
No plano gratuito, compute esgotado suspende conexões até renovação ou upgrade;
não significa que os dados foram apagados. Não excluir projeto nem recriar banco
para tentar recuperar a conta. Upgrade é uma decisão de custo separada.

Flyway e Hibernate precisam de conexão na inicialização. Enquanto a cota estiver
bloqueada, um deploy novo pode falhar; não desligar migrações nem validação para
contornar isso. Após recuperar a cota, publicar novamente se necessário e conferir
o deploy, `vivo`, `pronto` e login com credencial legítima. O endpoint `vivo` sozinho
não comprova que o banco está disponível.

Depois de publicar, fechar as abas do sistema e o SQL Editor, aguardar pelo menos
dez minutos e observar no painel do Neon se o compute suspende. As métricas podem
atrasar. Comparar consumo em janelas de ociosidade e de uso; não prometer economia
percentual antes da medição. Não testar isso repetindo chamadas a `pronto`.

Referências: [HikariCP](https://github.com/brettwooldridge/HikariCP#frequently-used),
[suspensão do Neon](https://neon.com/docs/introduction/scale-to-zero) e
[healthchecks do Render](https://render.com/docs/health-checks).
