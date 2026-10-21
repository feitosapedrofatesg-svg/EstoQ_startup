# Homologação detalhada — candidata de 21/10/2026

Status: **aprovada tecnicamente; aguardando aprovação funcional e promoção**.
Este documento registra o que está publicado na homologação, o que foi testado,
as limitações da validação e o procedimento de promoção para produção.

## Identificação

| Item | Valor |
| --- | --- |
| Data da validação | 21/10/2026, até 19:27 UTC |
| Candidata | `414175d2557a9fbda1d6fb0cf43b1b7d2c65178f` |
| Produção (`main`) | `683375a1b3cf38cdb924dd033a29f3213863b3cb` |
| Ambiente | `homologacao` |
| Frontend | https://estoq-homologacao.vercel.app |
| Backend | https://estoq-backend-homologacao.onrender.com |
| Projeto Vercel | `estoq-homologacao` (`prj_2e1RTTg0xblYT98RWH3o8UJItsks`) |
| Deployment Vercel | `dpl_7mE6gF3fjp5BAf7HMADZK4VR8FDG` |
| Serviço Render | `srv-dbc8668n74is73fmt13g` |
| Deploy Render | `dep-dbcg2guk1f9s7393bf2g` |
| Banco | Supabase exclusivo `kklnvrihortzogvdpbfu`, us-west-2 |
| CI `dev` | Execução `39650387998`, aprovada |
| CI `homologacao` | Execução `39650748323`, aprovada |

O frontend foi publicado às 18:13:19 UTC e ficou pronto às 18:13:34 UTC. O
backend ficou `live` às 18:19:13 UTC. Os dois deployments apontam para o mesmo
commit `414175d`, e `/api/saude/vivo` confirma `ambiente: homologacao` e essa
versão. A produção continua em `683375a` e não recebeu estes commits.

## Escopo da candidata

A candidata está **8 commits à frente da produção**. Promover `414175d` leva
todos eles, não apenas as duas últimas mudanças de frontend:

| Commit | Tipo | Resumo |
| --- | --- | --- |
| `5f55eed` | CI | Cria a etapa de validação antes da produção. |
| `c7e0361` | Homologação | Prepara ambiente isolado, perfil de backend e medição de latência. |
| `9b644ce` | Documentação | Registra o ambiente online e as evidências de latência. |
| `e6659de` | Frontend | Aplica cores Nutrigo e Poppins sem alterar medidas. |
| `5e33160` | Frontend | Destaca desperdício em vermelho e ajusta o sino. |
| `fb9a128` | Frontend | Aplica laranja forte no ponto e em “Marcar como visto”. |
| `66e434e` | Frontend | Adiciona “Importar nota fiscal” ao lado de “Novo produto” no Catálogo. |
| `414175d` | Frontend/perfil | Remove Movimentações do menu e do acesso da cozinha. |

As mudanças de interface estão descritas em
[FRONTEND_CATALOGO_E_PERFIS.md](FRONTEND_CATALOGO_E_PERFIS.md) e
[TEMA_NUTRIGO.md](TEMA_NUTRIGO.md). Antes de promover, revise também `c7e0361`,
que altera configuração de deploy e o perfil de homologação do backend.

## Validação automatizada

| Verificação | Resultado |
| --- | --- |
| Build do frontend (`tsc --noEmit && vite build`) | Aprovado |
| Testes de configuração do frontend (`npm run test:infra`) | 5 aprovados |
| Playwright no CI | **89 aprovados** |
| Backend (`mvn test`) no CI de dev | Aprovado |
| Backend (`mvn test`) no CI de homologação | Aprovado |
| `git diff --check` | Sem erros |

A suíte de interface cobre o novo teste “cozinha não vê nem acessa a tela de
movimentações”, além dos testes existentes de catálogo, tema, responsividade,
cache, alertas, notas e conciliação.

## Validação no ambiente publicado

O roteiro usou login real na conta ADMIN exclusiva de homologação. Nenhuma senha
ou cookie foi registrado nos resultados. Não houve escrita de produtos, notas,
consumo ou estoque durante esta conferência.

### Saúde e versão

| Verificação | Resultado |
| --- | --- |
| `/api/saude/vivo` pelo backend | HTTP 200, `ambiente: homologacao`, versão `414175d` |
| `/api/saude/pronto` pelo backend | HTTP 200 |
| `/api/saude/vivo` pelo domínio Vercel | HTTP 200 com a mesma versão |
| `/api/saude/pronto` pelo domínio Vercel | HTTP 200 |

Na primeira chamada após ociosidade, o proxy do Vercel respondeu
`ROUTER_EXTERNAL_TARGET_ERROR` enquanto o Render iniciava. Depois do acesso
direto ao backend, as duas rotas de saúde passaram a responder 200. É o
comportamento esperado do plano gratuito, não uma regressão da candidata.

### Tema e responsividade

Foram verificadas cinco rotas — `/dashboard`, `/catalogo`, `/consumo`,
`/relatorios` e `/balanco` — em 1440, 390 e 320 px, totalizando **15 combinações**.

- Poppins carregada e acentos renderizados.
- Largura da página sem overflow.
- Zero erros JavaScript.
- Zero requisições aos hosts de produção.

As cores publicadas também foram conferidas por CSS computado:

| Elemento | Valor confirmado |
| --- | --- |
| Barra de desperdício | `rgb(190, 19, 40)` / `#BE1328` |
| Trilho da barra | `rgb(255, 240, 242)` / `#FFF0F2` |
| Ponto de notificação | `rgb(239, 115, 32)` / `#EF7320`, 8 × 8 px |
| “Marcar como visto” | `rgb(200, 82, 0)` / `#C85200` |

A conta de teste não tinha alertas abertos no momento. Por isso, o ponto e a ação
de marcar como visto foram confirmados pelo CSS publicado e pelos testes locais,
não por uma lista real com alertas.

### Catálogo

| Largura | Resultado |
| --- | --- |
| 1440 px | Atalho à esquerda de “Novo produto”, 10 px de separação, sem overflow. |
| 390 px | Botões em largura total, empilhados, sem overflow. |
| 320 px | Botões em largura total, empilhados, sem overflow. |

O link aponta para `/notas-fiscais` e o clique chegou à tela de importação. Não
houve erro JavaScript.

### Permissões da cozinha

| Perfil | Menu | Acesso direto a `/movimentacoes` |
| --- | --- | --- |
| ADMIN | Item presente | Tela aberta normalmente |
| COZINHA | Item ausente | Mensagem de área indisponível |

A conta de teste é ADMIN. Para conferir o comportamento da cozinha no site
publicado, a resposta de `/api/auth/me` foi substituída apenas no navegador de
teste por um perfil COZINHA, sem alteração no servidor. O menu e o bloqueio da
rota foram confirmados dessa forma. Uma autenticação real de cozinha continua
recomendada antes da aprovação, caso exista uma credencial de teste desse perfil.

## Limitações desta rodada

- A validação funcional não executou entradas, consumo, notas ou balanço com
  escrita real. Esses fluxos continuam cobertos pelos testes automatizados e
  pelo CI, mas não foram repetidos manualmente nesta candidata.
- O perfil COZINHA foi simulado no navegador, conforme descrito acima.
- A visualização do ponto e da ação de notificação com alertas reais não foi
  possível porque a conta estava sem alertas.
- A medição de latência de 80 consultas registrada em
  [DEPLOY_HOMOLOGACAO.md](DEPLOY_HOMOLOGACAO.md) foi feita na preparação do
  ambiente; não foi repetida para `414175d`.
- O Render gratuito pode dormir. O primeiro acesso após inatividade pode demorar
  ou falhar no proxy até o backend despertar.

## Critérios de promoção

- [x] CI de `dev` e `homologacao` aprovada.
- [x] Build e 89 testes Playwright aprovados.
- [x] Backend e frontend publicados no commit `414175d`.
- [x] Saúde e versão confirmadas nos dois domínios.
- [x] Tema, responsividade, Catálogo e bloqueio da cozinha conferidos.
- [ ] Aprovação funcional do responsável.
- [ ] Promoção por fast-forward do hash aprovado.

## Promoção para produção

Somente depois da aprovação e com o diretório limpo:

```bash
git fetch origin
git switch main
git merge --ff-only origin/main
git merge --ff-only 414175d2557a9fbda1d6fb0cf43b1b7d2c65178f
git push origin main
```

Não usar force push nem merge de `dev` diretamente em `main`. O hash promovido
deve ser exatamente o que foi validado na homologação.

Após o push, conferir:

1. CI da `main` aprovada.
2. Deployment Vercel e Render no SHA promovido.
3. `/api/saude/vivo` e `/api/saude/pronto` em produção.
4. Login, Catálogo e ausência de Movimentações para a cozinha.
5. Ausência de erros JavaScript e de regressões visuais nas rotas principais.

## Reversão

As mudanças de frontend não incluem migração de banco. Se houver regressão,
reverta o commit específico na `dev`, valide novamente na homologação e publique
um novo commit aprovado. Não reescreva o histórico da `main`. A reversão de
`414175d` devolve o acesso à cozinha; a reversão de `66e434e` remove apenas o
atalho de nota do Catálogo.

## Referências

- [Fluxo de homologação](HOMOLOGACAO.md)
- [Configuração do ambiente](DEPLOY_HOMOLOGACAO.md)
- [Mudanças de frontend](FRONTEND_CATALOGO_E_PERFIS.md)
- [Tema Nutrigo](TEMA_NUTRIGO.md)
- [Histórico de mudanças](HISTORICO_DE_MUDANCAS.md)
