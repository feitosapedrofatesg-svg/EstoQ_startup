# Correções de segurança e estoque

Revisão de 18/10/2026. Este anexo atualiza os contratos da revisão de 12/10, preservados nos arquivos Word e Markdown históricos. O código mantém o monólito em camadas PIAds3, com métodos simples e responsabilidades separadas.

## Horários e relatórios

Eventos continuam gravados em UTC. O dia de operação usa `America/Sao_Paulo`, equivalente ao horário da Bahia nas datas atuais. `HorarioOperacao` centraliza a conversão e aceita um `Clock` para testes.

Uma compra às 22h de 17/10 fica armazenada como 01h UTC de 18/10 e pertence ao relatório de 17/10. O intervalo inclui o início e exclui o início do dia seguinte. A regra vale para JSON, PDF, dashboard, médias, agrupamentos semanais e mensais. Validade e agendamento continuam sendo datas, sem conversão do valor cadastrado.

Relatórios aceitam intervalos de até 3660 dias. Reposição aceita de 1 a 365 dias. Consultas de consumo e desperdício filtram as duas pontas do intervalo no banco. Produtos desativados continuam identificáveis no histórico de consumo.

O gráfico por dia da semana mostra **custo em reais**, com `valorTotal` e `valorMedio`, substituindo a soma de quantidades em unidades incompatíveis. Desperdício preserva a unidade de cada produto; o agrupamento por motivo soma o prejuízo monetário. O mês recebido como `YYYY-MM` é exibido corretamente como `MM/YYYY`.

## Estoque e alertas

Saldo físico continua incluindo lotes vencidos. Reposição e estoque mínimo usam saldo disponível, excluindo vencidos e lotes sem quantidade.

A consulta de alertas reavalia o estado do estoque dentro do restaurante autenticado. Assim, a primeira consulta após a mudança do dia detecta vencimentos mesmo sem movimentação. A mesma reconciliação recupera alertas que não tenham sido atualizados após uma gravação. Não há scheduler global executando como tenant raiz.

Lote que venceu perde o alerta ativo de próximo vencimento; lote esgotado perde o aviso ativo de validade. Um aviso marcado como visto não reaparece a cada consulta. A antecedência pode ser atualizada sem apagar essa marcação. O processamento individual consulta apenas os produtos afetados e evita uma busca por produto.

## Concorrência e auditoria

Alterações de usuários bloqueiam o restaurante antes de conferir o último administrador. Duas desativações simultâneas preservam pelo menos um ADMIN ativo. A proteção foi testada com PostgreSQL.

A auditoria de restaurante registra o identificador do responsável em criação, atualização e desativação de usuários. As movimentações continuam auditadas. A tabela separada `auditoria_plataforma` registra operador, restaurante alvo, ação e usuário alvo quando aplicável. Suspensão, reativação e redefinição de senha entram nessa trilha. Senhas e hashes não entram nos registros.

O script administrativo de limpeza inclui a tabela nova e continua restrito aos restaurantes selecionados no manifesto, preservando a auditoria das demais lojas.

A migração nova é `V8__auditoria_administrativa.sql`; migrações anteriores permanecem inalteradas. Ela acrescenta `auditoria.usuario_id` e cria a tabela global. Os registros antigos podem continuar sem identificador do ator. Não existe preenchimento inventado do histórico.

## Impressão e acesso

A impressão do balanço monta os campos dinâmicos com `textContent`. Um nome contendo tags aparece como texto e não executa HTML. Os modais contêm Tab/Shift+Tab, deixam o fundo inerte e devolvem o foco ao botão de origem.

Login mantém o limite do par e-mail/origem e acrescenta limites independentes por conta e origem. O sucesso limpa o histórico da conta/par, mas não a cota da origem. As estruturas têm teto de 10 mil chaves, com expiração; atingir a capacidade recusa novas chaves, sem apagar bloqueios ativos.

O cadastro público limita a origem a dez pedidos por hora, incluindo tentativas recusadas pelo domínio. A configuração é `estoq.cadastro.max-tentativas`. Os limites são locais à instância. E-mail é limitado a 254 caracteres antes de entrar no controle de login.

Não habilitar confiança irrestrita em `X-Forwarded-For`: o endereço real e a lista de proxies confiáveis devem ser confirmados no provedor antes de configurar o encaminhamento de IP. Os limites não aceitam esse header diretamente.

## Dependências e CI

- Spring Boot 4.0.8, mantendo Java 21.
- PostgreSQL JDBC 42.7.14 e Tomcat 11.0.27.
- BOM Jackson 2.21.7 e 3.1.7 para correções posteriores ao BOM do Boot.

A CI gera a lista Maven resolvida e consulta o OSV com `scripts/verificar_dependencias.py`. O script envia apenas coordenadas públicas e versões; falha de rede ou lista vazia não é considerada aprovação. Avisos encontrados interrompem a verificação para revisão. O frontend executa `npm audit --audit-level=high`.

Na conferência local desta revisão, o OSV retornou zero avisos para 107 dependências e o npm audit retornou zero vulnerabilidades. Isso descreve o banco de avisos consultado naquele momento, não uma garantia absoluta de segurança.

## Produção e provisionamento

O Blueprint Render fixa o perfil `prod`; esse perfil usa Flyway e Hibernate `validate`. Os endpoints públicos `GET /api/saude/vivo` e `GET /api/saude/pronto` não criam sessão nem expõem a configuração do banco. O primeiro verifica o processo; o segundo verifica a conexão. O keepalive usa o primeiro e o health check Render usa o segundo.

O frontend recebe Referrer-Policy, Permissions-Policy e CSP em modo de relatório. A CSP deve ser observada no navegador antes de passar ao modo bloqueador. O domínio estável confirmado é `https://estoq-ph-feit0sa.vercel.app`; o endereço antigo do homepage retornava 404.

`scripts/bootstrap-producao.sql` não contém senha nem hash fixo. É executado manualmente via `psql`, com e-mail, nome e hash BCrypt de uma senha única gerada de forma local. Cria uma conta PLATAFORMA ou troca a senha de uma PLATAFORMA ativa existente. Recusa transformar uma conta de restaurante em operador global e não cria configurações sem restaurante.

Remover o segredo do script não invalida uma senha já utilizada. A troca real exige a sessão administrativa legítima do banco/plataforma. Após a troca, conferir revogação de sessões HTTP e SSE e examinar registros disponíveis de acesso. Não repetir a senha antiga em comandos ou tentar autenticar com ela para demonstrar a exposição.

Backup continua completo, manual e exclusivo da PLATAFORMA. A cópia externa automática depende de definir destino, retenção e permissões; não enviar dumps para um serviço escolhido implicitamente. Validar restauração em banco separado. A configuração de armazenamento externo e a rotação real da credencial são tarefas operacionais separadas da mudança no código.

O script `scripts/e2e_validacao.sh` agora chama a integração local de sessões HTTP e PostgreSQL descartável, sem credencial ou escrita em produção.

## API ajustada

- `GET /api/movimentacoes`: aceita `dataInicio` e `dataFim` como datas locais, com o dia final inteiro incluído. Os parâmetros antigos `inicio`/`fim` continuam disponíveis como horários UTC; não misturar os formatos na mesma consulta.
- `GET /api/relatorios/consumo/dia-semana`: retorna `diaSemana`, `valorTotal` e `valorMedio`, em reais.
- `DesperdicioDTO`: acrescenta `unidadeMedida`.
- Configuração de balanço sem periodicidade e meta CMV inválida retornam 400.
- Novos endpoints de saúde são públicos e não carregam token CSRF.

Publicar backend e frontend compatíveis. Clientes antigos podem precisar recarregar a página para receber os campos novos do gráfico.

## Validação local

A suíte Java contabilizou 127 testes, sem falhas ou erros: 124 aprovados e três opcionais ignorados (OCR real e documentos externos não fornecidos). Os 56 testes Playwright passaram; o build TypeScript/Vite também passou. Os dez testes Python dos scripts passaram, e `git diff --check` não apontou problemas. A CI instala Tesseract e executa também o caso de OCR com documentos fictícios.
