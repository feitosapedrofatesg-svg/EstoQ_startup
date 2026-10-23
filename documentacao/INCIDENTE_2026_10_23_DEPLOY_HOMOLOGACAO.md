# Incidente de 23/10/2026 — falha no deploy da homologação (exportação de relatórios)

Relato técnico do erro que manteve a homologação na versão anterior e impediu a
exportação em CSV. Ambiente afetado: **homologação**. Produção não foi alterada.

## Resumo

O commit `d443fef` introduziu a exportação unificada de relatórios (PDF e CSV) em
um controller novo, `RelatorioExportController`. Como o mesmo caminho
`GET /api/relatorios/exportar` foi mapeado também no `RelatoriosController`, o
Spring recusou a inicialização por **mapeamento ambíguo**. O build passava, mas a
aplicação terminava com código de saída diferente de zero, e o Render marcava o
deploy como `update_failed`.

Consequência: o backend de homologação continuou em `8c2f377` (sem o endpoint
novo) enquanto o frontend já estava publicado com a nova tela. O PDF ainda
funcionava pela rota antiga; o CSV retornava 404 e a interface mostrava
"Recurso não encontrado".

## Impacto

| Item | Situação |
| --- | --- |
| Produção | Sem impacto; permaneceu em `b704439` |
| Homologação — PDF | Funcionou durante todo o incidente (rota antiga) |
| Homologação — CSV | Indisponível até `5a2a2a3` |
| Dados | Nenhuma migração, escrita ou perda de dados |
| Usuários | Apenas o ambiente de homologação |

## Linha do tempo (horário de Brasília)

| Hora | Evento |
| --- | --- |
| 15:34 | `d443fef` publicada em `dev` e `homologacao` |
| 16:42 | Deploy `d4c27cd` disparado no Render |
| 16:43 | Build concluído com sucesso (`buildStatus: succeeded`) |
| 16:47 | Deploy falha na subida: `update_failed`, `nonZeroExit: 1` |
| 16:54 | `5a2a2a3` publicada; novo deploy disparado |
| 16:59 | Deploy `live` em `5a2a2a3`; homologação volta a funcionar |
| 17:01 | `8847fda` remove o fallback temporário e o código de diagnóstico |

## Causa raiz

Dois beans declarando a mesma rota:

```java
// RelatorioExportController.java (removido)
@GetMapping("/api/relatorios/exportar")

// RelatoriosController.java (mantido)
@GetMapping("/api/relatorios/exportar")
```

O Spring registra os handlers no startup e, ao encontrar o mesmo par
método + caminho, lança `IllegalStateException: Ambiguous mapping`. Como o
erro ocorre na inicialização do contexto, o processo encerra antes de abrir a
porta. O Render classifica isso como falha de deploy, não de build.

A duplicação aconteceu porque, durante o diagnóstico do 404, o endpoint foi
adicionado ao `RelatoriosController` sem remover o controller criado antes. O
código anterior não foi consultado por completo antes da segunda alteração.

## Por que passou pelos testes

- `mvn test` não sobe o contexto completo em todos os testes; os testes de PDF
  instanciam o serviço diretamente, sem o Spring.
- O CI compila e roda testes, mas não executa um smoke test de inicialização
  com todos os controllers registrados.
- A validação em homologação foi feita depois do deploy, sem checar o status do
  deploy do backend antes de testar a tela.

## Correção aplicada

1. Removido o `RelatorioExportController` duplicado, mantendo apenas a rota em
   `RelatoriosController`.
2. Deploy manual no Render e acompanhamento até `live`.
3. Verificação autenticada dos quatro formatos e relatórios representativos.
4. Removido o fallback temporário para a rota antiga de PDF e o código de
   diagnóstico (`8847fda`).

## Evidências da validação

- `GET /api/saude/vivo` → `{"ambiente":"homologacao","versao":"5a2a2a3..."}`.
- CSV autenticado: `200`, `text/csv;charset=UTF-8`, com
  `Content-Disposition` correto e BOM para Excel em pt-BR.
- Amostras: `estoque-atual` com 104 linhas, `cmv` com 11, `faturamento` com 26.
- Interface: download de `estoQ-estoque-atual-2026-10-23-a-2026-10-23.csv`
  concluído, cabeçalho `Produto;Categoria;Unidade;Saldo;Valor em estoque`,
  104 linhas e zero erros de JavaScript.

## Prevenção

- Antes de acrescentar uma rota, buscar o caminho no projeto inteiro
  (`grep -rn "/api/..."`), não apenas no arquivo em edição.
- Não criar controller novo para uma rota que já existe em outro.
- No diagnóstico de deploy, conferir o status no Render **antes** de testar a
  tela; `build_ended` com sucesso não garante aplicação no ar.
- Adicionar ao CI um teste de contexto Spring que carregue todos os
  controllers, detectando mapeamentos ambíguos antes do deploy.
- Validar em homologação somente depois de `saude/vivo` reportar o commit
  esperado.

## Regra derivada

Falha de deploy com build verde deve ser tratada como erro de aplicação. O
critério de pronto do backend é: build, testes e **health check respondendo com
a versão do commit publicado**.
