# Melhorias do EstoQ — 27/11/2026

Implementação realizada em uma cópia isolada, na branch dev. As alterações locais
da cópia original `/home/pedro/estoq-cmv` foram preservadas. O pedido final autoriza
publicar dev → homologacao e parar antes de main. Sem force push.

## Entrega por requisito

| Requisito | Situação | Resultado e limites |
| --- | --- | --- |
| Perfil Operacional | Concluído | COZINHA + POST entradas. Cadastro/edição, navegação e API. COZINHA não ganha entrada; usuários existentes mantêm perfil. |
| Planilha de estoque | Concluído | Apenas Planilha na interface de estoque; XLSX tipado, identificação da loja, emissão, filtros, cabeçalho, larguras, filtros de coluna e congelamento. Todos os registros filtrados; dinheiro totalizado e quantidades separadas por unidade. |
| Próximo do vencimento | Concluído | Parâmetro existente diasAlertaVencimento, com a mesma regra e fuso dos alertas. Considera saldo de lotes; vencido e próximo podem coexistir no produto agrupado. |
| Paginação | Concluído | Estoque, lista de balanços e lotes para baixa no backend, com ordem estável e 10/25/50. Itens de cada balanço usam paginação local mantendo o DTO completo para conferência, impressão e preservação dos rascunhos. |
| Desperdício | Concluído | Saldo e unidade antes da baixa, por produto ou lote; atualização após salvar. Mantida baixa por movimentação, custeio e proteção otimista de concorrência existentes. |
| Consumo do dia | Concluído | Data selecionável, total de todos os registros e impressão do mesmo conjunto. Custo histórico existente preservado, sem depender da página da tela. |
| Etiquetas | Parcial na validação física | Implementadas prévia, quantidade, dimensões/margens em mm e impressão/reimpressão do lote salvo. PDF e navegador validados; nenhuma impressora física disponível. |
| Temas e fonte | Concluído | Claro inicial independente do sistema; Escuro, Original e quatro paletas separadas. Fonte 90–125% via tipografia, sem transformar a página; etiquetas mantêm mm/pt próprios. |
| Configurações e suporte | Concluído | Engrenagem ao lado do sino, todos os perfis, teclado/Escape/foco, consulta aos documentos, cópia e mailto do suporte. |
| Idiomas | Concluído na implementação | pt-BR/es/en, catálogos centralizados, troca sem login ou remontagem das telas, preferência por usuário, fallback português. Nomes cadastrados e BRL preservados. Documentos jurídicos continuam em português. |
| Confirmações no cadastro | Parcial no conteúdo jurídico | Fluxos local/Google e validação no backend concluídos; dois registros atômicos com versões e horário UTC do servidor. Termos são minuta solicitada pelo responsável e dependem de revisão antes de produção. Política existente reutilizada. Nenhum aceite retroativo. |

## Arquitetura e dados

Reutilizados Spring Security, TenantEntity, serviços de estoque/lotes/baixas,
HorarioOperacao, DTOs de movimentações, Modal, Button e Paginacao. Os endpoints
antigos de listagem foram preservados para formulários existentes. Consultas,
impressão e exportação usam o restaurante da sessão.

A exportação anterior era HTML apresentado como XLS e não mantinha células
numéricas/datas reais. Apache POI 6.0.0 foi a única dependência de aplicação
adicionada, para gerar XLSX real. Não foi adicionada biblioteca de UI ou protocolo
de impressora. Não existe serviço externo de tradução em execução.

Migrações: V19 amplia o domínio do perfil; V20 acrescenta preferências com
padrões; V21 cria aceites_documentos, com vínculo de responsável/restaurante.
Não fazem backfill de aceites, troca de perfil, exclusão de registros nem cópia
de dados entre restaurantes. A proteção da limpeza administrativa foi atualizada
para conhecer a nova tabela e manter a ordem de FKs; o script não foi executado
contra bancos remotos nesta implementação.

## Verificações locais realizadas

- Backend local completo: 243 testes, zero falhas/erros; três ignorados por ausência de OCR configurado e/ou documentos reais locais.
- CI da dev (implementação 19042ff): 244 testes backend, zero falhas/erros, dois ignorados por dependerem de documentos reais não versionados; 184 testes frontend aprovados. OCR com documentos fictícios foi executado na CI.
- CI: https://github.com/feitosapedrofatesg-svg/EstoQ_startup/actions/runs/45983856178
- API depois das últimas adições: 35 testes passaram; nova rodada de paginação e isolamento: 17 passaram. Há um teste novo além da suíte completa anterior.
- Frontend completo: 182 testes passaram; dois casos adicionais de cadastro e desperdício passaram.
- Revisão final das funcionalidades novas: 15 testes passaram, incluindo todos os perfis, 375/768/1440 px, sete temas, fonte 125%, inglês/espanhol, persistência, foco e clipboard.
- Contagem do balanço preservada ao mudar página de itens e de balanços.
- API: entrada real do Operacional com HTTP 201 e leitura do lote, Cozinha com HTTP 403, operações administrativas negadas e preferências separadas.
- API: limites de validade alterados pelo administrador, lotes mistos e sem validade, páginas distintas, isolamento entre duas lojas e planilha com 29 produtos apesar da página de dez.
- Planilha aberta com POI: tipos numéricos, data, total, filtros e congelamento. PDF em inglês preservou nome cadastrado e BRL.
- Concorrência de desperdício com PostgreSQL real: uma baixa aceita, outra recusada e saldo final não negativo, sem movimentação duplicada.
- Impressão de consumo: 35 registros de custo histórico com total R$ 70 na tela e na impressão, apesar de 30 registros por página. Dia vazio: total zero e impressão desabilitada.
- Etiquetas: prévia com nome/lote/validade reais do registro; conteúdo tratado como texto; reimpressão sem POST de entrada. PDF de três etiquetas: três páginas, cerca de 60 × 40 mm, sem página extra. Prévia e configurações inspecionadas visualmente.
- Build TypeScript/Vite e compilação Maven passaram. Vite informa aviso não impeditivo de bundle acima de 500 kB.
- Infraestrutura: 17 testes Python e um teste Node passaram. Corrigida leitura ANSI do relatório Maven para não omitir dependências da consulta OSV.
- Segurança de dependências: npm audit sem vulnerabilidades; OSV verificou 131 dependências, zero avisos.

As verificações de interface usam dados fictícios e mocks; testes de backend
exercitam H2 e PostgreSQL/HTTP reais conforme a suíte existente. Os resultados
não representam validação física de impressoras, de todos os drivers ou de
todos os navegadores/dispositivos reais. Traduções foram implementadas e
verificadas em fluxos representativos, sem revisão linguística profissional.

O backend de homologação registrou aplicação bem-sucedida de V19, V20 e V21,
sem apagar dados. A promoção deve manter o mesmo commit validado; a disponibilidade
final do site e o commit publicado são conferidos depois do deploy.

## Etiquetas: configuração na impressora

Escolha no navegador a impressora instalada no sistema. No driver e no diálogo
de impressão, configure o mesmo papel da etiqueta, margens compatíveis e escala
de 100%; desative cabeçalhos/rodapés. Confira a prévia antes de imprimir. O driver
pode impor margens mínimas ou arredondar o papel; ajuste largura/altura/margens
no painel e no driver. A redução de texto tem limite e bloqueia conteúdo que
não cabe. Isso não garante compatibilidade universal.

Cancelar ou falhar a impressão não desfaz a entrada: ela já está salva. Para
tentar novamente, use Reimprimir etiquetas em Entradas ou a ação da movimentação
de entrada. Lote ausente bloqueia impressão; validade ausente fica sinalizada,
sem data inventada.

## Pendências antes de produção

- Revisar e aprovar juridicamente a minuta dos Termos; confirmar versão final.
- Validar papel, driver e alinhamento em uma impressora física.
- Fazer aceite operacional pelos responsáveis na homologação. Não houve promoção para main nesta entrega.

## Arquivos alterados

Lista de arquivos da implementação (fora de node_modules, builds, segredos e evidências temporárias):

- `backend/pom.xml`
- `backend/src/main/java/com/estoq/business/alertas/AlertaController.java`
- `backend/src/main/java/com/estoq/business/balancos/BalancoController.java`
- `backend/src/main/java/com/estoq/business/balancos/BalancoService.java`
- `backend/src/main/java/com/estoq/business/balancos/IBalancoRepository.java`
- `backend/src/main/java/com/estoq/business/loginGoogle/GoogleCadastroDTO.java`
- `backend/src/main/java/com/estoq/business/loginGoogle/GoogleLoginService.java`
- `backend/src/main/java/com/estoq/business/lotes/ILoteRepository.java`
- `backend/src/main/java/com/estoq/business/lotes/LoteAdapter.java`
- `backend/src/main/java/com/estoq/business/lotes/LoteController.java`
- `backend/src/main/java/com/estoq/business/lotes/LoteDTO.java`
- `backend/src/main/java/com/estoq/business/lotes/LoteService.java`
- `backend/src/main/java/com/estoq/business/produtos/EstoqueController.java`
- `backend/src/main/java/com/estoq/business/produtos/EstoqueDTO.java`
- `backend/src/main/java/com/estoq/business/produtos/EstoqueService.java`
- `backend/src/main/java/com/estoq/business/produtos/IProdutoRepository.java`
- `backend/src/main/java/com/estoq/business/registro/AceiteDocumentoModel.java`
- `backend/src/main/java/com/estoq/business/registro/DocumentosController.java`
- `backend/src/main/java/com/estoq/business/registro/DocumentosService.java`
- `backend/src/main/java/com/estoq/business/registro/IAceiteDocumentoRepository.java`
- `backend/src/main/java/com/estoq/business/registro/RegistroRequestDTO.java`
- `backend/src/main/java/com/estoq/business/registro/RegistroService.java`
- `backend/src/main/java/com/estoq/business/relatorios/FormatoRelatorioPdf.java`
- `backend/src/main/java/com/estoq/business/relatorios/LayoutRelatorioPdf.java`
- `backend/src/main/java/com/estoq/business/relatorios/PlanilhaEstoqueService.java`
- `backend/src/main/java/com/estoq/business/relatorios/PreparadorRelatorioService.java`
- `backend/src/main/java/com/estoq/business/relatorios/TotaisRelatorioPdf.java`
- `backend/src/main/java/com/estoq/business/usuarios/Perfil.java`
- `backend/src/main/java/com/estoq/business/usuarios/PreferenciasController.java`
- `backend/src/main/java/com/estoq/business/usuarios/PreferenciasDTO.java`
- `backend/src/main/java/com/estoq/business/usuarios/UsuarioModel.java`
- `backend/src/main/java/com/estoq/config/security/SecurityConfig.java`
- `backend/src/main/java/com/estoq/core/helpers/Idioma.java`
- `backend/src/main/resources/db/migration/V19__perfil_operacional.sql`
- `backend/src/main/resources/db/migration/V20__preferencias_usuario.sql`
- `backend/src/main/resources/db/migration/V21__aceites_documentos.sql`
- `backend/src/main/resources/i18n/relatorios_en.json`
- `backend/src/main/resources/i18n/relatorios_es.json`
- `backend/src/test/java/com/estoq/ConfirmacaoEmailIntegrationTest.java`
- `backend/src/test/java/com/estoq/GoogleLoginIntegrationTest.java`
- `backend/src/test/java/com/estoq/GoogleOAuthIntegrationTest.java`
- `backend/src/test/java/com/estoq/MigracaoProdValidacaoTest.java`
- `backend/src/test/java/com/estoq/RegistroIntegrationTest.java`
- `backend/src/test/java/com/estoq/SecurityIntegrationTest.java`
- `backend/src/test/java/com/estoq/SincronizacaoBalancoIntegrationTest.java`
- `backend/src/test/java/com/estoq/SupabasePreparacaoIntegrationTest.java`
- `backend/src/test/java/com/estoq/TenantIsolamentoIntegrationTest.java`
- `documentacao/API_ENDPOINTS.md`
- `frontend/src/App.tsx`
- `frontend/src/components/Carrossel.tsx`
- `frontend/src/components/Charts.tsx`
- `frontend/src/components/Configuracoes.tsx`
- `frontend/src/components/ConfirmacoesCadastro.tsx`
- `frontend/src/components/Etiquetas.tsx`
- `frontend/src/components/FaturamentoDiario.tsx`
- `frontend/src/components/GoogleButton.tsx`
- `frontend/src/components/Icon.tsx`
- `frontend/src/components/Layout.tsx`
- `frontend/src/components/PaginaPublica.tsx`
- `frontend/src/components/Paginacao.tsx`
- `frontend/src/components/ProdutoAbertoModals.tsx`
- `frontend/src/components/ProximoBalanco.tsx`
- `frontend/src/components/balanco/CalendarioBalanco.tsx`
- `frontend/src/components/balanco/ConfigBalanco.tsx`
- `frontend/src/components/balanco/ImprimirBalanco.ts`
- `frontend/src/components/balanco/ItensContagem.tsx`
- `frontend/src/components/balanco/NovoBalanco.tsx`
- `frontend/src/components/consumo/ConsumoRapido.tsx`
- `frontend/src/components/consumo/ImprimirConsumo.ts`
- `frontend/src/components/consumo/PainelQuantidade.tsx`
- `frontend/src/components/consumo/TextoPorToque.tsx`
- `frontend/src/components/ui/Abas.tsx`
- `frontend/src/components/ui/Busca.tsx`
- `frontend/src/components/ui/Campos.tsx`
- `frontend/src/components/ui/Cartoes.tsx`
- `frontend/src/components/ui/Modal.tsx`
- `frontend/src/components/ui/Pagina.tsx`
- `frontend/src/components/ui/Tabela.tsx`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/format.ts`
- `frontend/src/lib/i18n.ts`
- `frontend/src/lib/perfis.ts`
- `frontend/src/lib/traducoes.ts`
- `frontend/src/lib/traducoesExtras.ts`
- `frontend/src/lib/types.ts`
- `frontend/src/main.tsx`
- `frontend/src/pages/Backup.tsx`
- `frontend/src/pages/Balanco.tsx`
- `frontend/src/pages/Catalogo.tsx`
- `frontend/src/pages/ConfirmacaoEmail.tsx`
- `frontend/src/pages/Consumo.tsx`
- `frontend/src/pages/Dashboard.tsx`
- `frontend/src/pages/Desperdicio.tsx`
- `frontend/src/pages/Entradas.tsx`
- `frontend/src/pages/Estoque.tsx`
- `frontend/src/pages/Home.tsx`
- `frontend/src/pages/Login.tsx`
- `frontend/src/pages/Movimentacoes.tsx`
- `frontend/src/pages/NotasFiscais.tsx`
- `frontend/src/pages/Plataforma.tsx`
- `frontend/src/pages/Privacidade.tsx`
- `frontend/src/pages/Registro.tsx`
- `frontend/src/pages/RegistroGoogle.tsx`
- `frontend/src/pages/Relatorios.tsx`
- `frontend/src/pages/Sobre.tsx`
- `frontend/src/pages/Termos.tsx`
- `frontend/src/pages/Usuarios.tsx`
- `frontend/src/store/auth.tsx`
- `frontend/src/store/preferencias.tsx`
- `frontend/src/store/toast.tsx`
- `frontend/src/temas.css`
- `frontend/tests/balanco-contagem-sistema.spec.ts`
- `frontend/tests/balanco-sem-itens.spec.ts`
- `frontend/tests/cache.spec.ts`
- `frontend/tests/confirmacao-email.spec.ts`
- `frontend/tests/confirmacao-senha.spec.ts`
- `frontend/tests/consumo-rapido.spec.ts`
- `frontend/tests/fixtures.ts`
- `frontend/tests/login-google.spec.ts`
- `frontend/tests/lote-fabricante.spec.ts`
- `frontend/tests/melhorias-cadastro-desperdicio.spec.ts`
- `frontend/tests/melhorias-configuracoes.spec.ts`
- `frontend/tests/melhorias-estoque-impressao.spec.ts`
- `frontend/tests/perfil-operacional.spec.ts`
- `frontend/tests/responsividade.spec.ts`
- `frontend/tests/seguranca-balanco.spec.ts`
- `frontend/tests/sincronizacao.spec.ts`
- `scripts/limpar-restaurantes-teste.sql`
- `scripts/tests/test_verificar_dependencias.py`
- `scripts/verificar_dependencias.py`
- `documentacao/API_ENDPOINTS.md`
- `documentacao/HISTORICO_DE_MUDANCAS.md`
- `documentacao/MELHORIAS_2026_11_27.md`
