# Documentação do EstoQ

Revisão: **12/10/2026**. Inclui as entregas de responsividade e consumo rápido.

O EstoQ é uma aplicação web multitenant para estoque, consumo, desperdício,
balanço e CMV de restaurantes. Cada restaurante compartilha seus dados entre
as próprias sessões e permanece isolado dos demais. O sistema exige conexão.

## Por onde começar

| Documento | Conteúdo |
| --- | --- |
| [Constituição](CONSTITUICAO.md) | Requisitos, regras e permissões consolidados; correspondente ao Word. |
| [Contrato de desenvolvimento](CONTRATO_DESENVOLVIMENTO.md) | Padrão de camadas, responsabilidades e critérios para evoluir o código. |
| [Resumo do sistema](RESUMO_SESSAO_BACKEND.md) | Arquitetura, módulos, regras, execução local e validação. |
| [API](API_ENDPOINTS.md) | Rotas, perfis, versões de balanço, autenticação e formatos. |
| [Notas fiscais](IMPORTACAO_NOTAS_FISCAIS.md) | XML, PDF, fotos, categorias e confirmação de entrada. |
| [Consumo rápido](CONSUMO_RAPIDO.md) | Seleção por toque, confirmação conjunta e impressão. |
| [Cache e sincronização](CACHE_E_SINCRONIZACAO.md) | Prazos, SSE, conflitos, rascunhos e desempenho. |
| [Backup](BACKUP_E_RESTAURACAO.md) | Backup completo exclusivo da PLATAFORMA e restauração. |
| [Deploy](DEPLOY_VERCEL_RENDER_NEON.md) | Configuração, migrações e verificação da publicação. |
| [Git e CI](COMMITS_E_FLUXO_DE_TRABALHO.md) | Branches dev/main, testes e publicação. |
| [Responsividade](RESPONSIVIDADE.md) | Layout, listas recolhidas e rolagem. |
| [Threads e concorrência](THREADS_E_SINGLETON.md) | Singleton didático, transações, versões e SSE. |
| [Histórico de mudanças](HISTORICO_DE_MUDANCAS.md) | Entregas recentes com os respectivos commits. |
| [Planilha de CMV](PLANILHA_CMV_RESTAURANTE.md) | Origem, limites e carga do catálogo de dezembro/2023. |

## Formatos e manutenção

Os três arquivos Word foram revisados junto com suas referências em Markdown:
`EstoQ_Constituicao_do_Projeto_v1.0.docx`,
`EstoQ_Prompt_Backend_Modular_Camadas_v1.0.docx` e
`RESUMO_SESSAO_BACKEND.docx`. Os nomes históricos foram mantidos para preservar
os links; a revisão atual consta no conteúdo. A constituição mantém os requisitos
originais e acrescenta a consolidação das decisões implementadas.

`ci.yml.example` espelha o workflow ativo em
[.github/workflows/validacao.yml](../.github/workflows/validacao.yml);
não é necessário ativar outro workflow.

`catalogo-cmv-dezembro-2023.csv` é um conjunto de dados de referência, com
182 produtos. Seus valores não foram alterados nesta revisão documental.
Notas fiscais reais, credenciais, dumps e arquivos gerados pelo build não pertencem
à documentação versionada.

Ao alterar o sistema, atualize o documento do módulo, os contratos afetados e o
histórico. Diferencie comportamento implementado, análise histórica e melhorias
futuras. Um check pendente da Vercel indica publicação em andamento, mesmo quando
o commit já chegou ao GitHub.
