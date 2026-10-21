# Como funciona a homologação

O fluxo é **dev → homologacao → main**.

| Branch | Para que serve |
| --- | --- |
| `dev` | Receber as funcionalidades e correções em desenvolvimento. |
| `homologacao` | Manter uma versão candidata para testes e aprovação. |
| `main` | Publicar a versão aprovada no site de produção. |

A branch `homologacao` começou no commit `683375a`, a mesma versão estável
que estava em dev e main. O primeiro commit novo configura a CI e este fluxo.
O GitHub Actions executa testes de backend, build, testes de interface e
verificações de dependências nas três branches, inclusive em pull requests.

## Exemplo prático

Suponha que uma melhoria na importação de notas esteja pronta na dev:

1. Aguarde os checks de backend e frontend da dev passarem no GitHub Actions.
2. Atualize homologacao com essa versão da dev.
3. Aguarde os checks de homologacao e confira os fluxos afetados em um ambiente
   de testes. Registre o hash do commit testado e o resultado.
4. Se encontrar erro, corrija na dev e leve a correção para homologacao.
   Teste novamente a versão resultante.
5. Depois da aprovação, leve **o commit aprovado** para main. O push em main
   dispara a publicação; confira CI, Render, Vercel e o site publicado.

Enquanto uma versão está em homologacao, a dev pode receber outras mudanças.
Elas só entram na candidata quando houver uma nova atualização de homologacao.
Atualizar a candidata exige repetir os testes e a aprovação.

## Comandos para atualizar a candidata

Com o diretório de trabalho limpo e a CI da dev aprovada:

```bash
git fetch origin
git switch homologacao
git merge --ff-only origin/homologacao
git merge --ff-only origin/dev
git push origin homologacao
git rev-parse HEAD
```

Guarde o hash exibido pelo último comando. Ele identifica exatamente o código
que será testado. Se `--ff-only` falhar, pare e confira a divergência;
não use force push para substituir o histórico.

## Comandos para publicar uma versão aprovada

Somente após aprovar a candidata e registrar seu hash:

```bash
git fetch origin
git switch main
git merge --ff-only origin/main
git merge --ff-only HASH_APROVADO
git push origin main
```

Substitua `HASH_APROVADO` pelo hash completo que foi testado em homologacao.
Usar esse hash evita publicar mudanças que chegaram depois na branch.
Se surgir conflito ou divergência, integre na dev, atualize homologacao e
valide uma nova candidata. Não resolva divergências diretamente na produção.

Para revisar pelo GitHub, abra um pull request com **base: homologacao** e
**compare: dev**. Para revisar a publicação, use **base: main** e **compare:
homologacao**, mantendo a candidata sem novos commits durante a revisão.
Faça as promoções pelos comandos acima para preservar o fast-forward;
as opções de merge e squash do GitHub podem criar históricos divergentes.

## Branch e ambiente de testes são coisas diferentes

A branch e a CI estão configuradas. Ainda não há um site de homologação com
backend e banco exclusivos definido neste fluxo. O Render de produção segue
main. O `frontend/vercel.json` encaminha `/api` para esse backend de produção;
um Preview do frontend com essa configuração também usa a API de produção.

Para testar cadastros, notas e estoque em um site separado, será necessário:

- Um banco/schema exclusivo com dados fictícios e migrações Flyway.
- Um backend ligado à branch homologacao e a esse banco.
- Um frontend de homologação com `/api` apontando para esse backend.
- Contas próprias de teste e configuração de sessão/CORS para esse endereço.

Até existir essa infraestrutura, use o ambiente local com banco de testes.
A CI usa bancos descartáveis e APIs fictícias nos testes de interface; ela
não altera o estoque da produção. CI verde valida os testes automatizados,
mas a aprovação funcional da candidata é uma etapa manual.

## Onde acompanhar

- [Código em homologacao](https://github.com/feitosapedrofatesg-svg/EstoQ_startup/tree/homologacao)
- [Validações da homologacao](https://github.com/feitosapedrofatesg-svg/EstoQ_startup/actions?query=branch%3Ahomologacao)
- [Fluxo de commits e validação](COMMITS_E_FLUXO_DE_TRABALHO.md)

As branches organizam o fluxo. Este ajuste não cria regras de proteção no GitHub
nem promove versões automaticamente para main.
