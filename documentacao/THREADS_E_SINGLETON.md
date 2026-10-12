# Threads, Singleton e concorrência

Revisão: **12/10/2026**. Projeto local: `/home/pedro/EstoQ`.

## Exemplo didático de consumo concorrente

`patterns/RegistradorConsumoSingleton` guarda uma instância estática `volatile`,
tem construtor privado e usa double-checked locking em `obter(ConsumoService)`.
O serviço é um campo final e a chamada delega ao proxy transacional do Spring.

O Singleton demonstra um padrão estudado; ele não substitui as transações nem
é o mecanismo que impede saldo inconsistente. A integridade do estoque depende
da validação e de `@Version` nas entidades persistidas.

`ConcorrenciaIntegrationTest` cria um lote com cinco unidades e executa duas
`Thread` com a mesma versão, cada uma consumindo uma unidade. Usa `join()` para
aguardar e `AtomicInteger` para contar resultados. Este teste não usa
CountDownLatch nem ExecutorService. Esperado: um sucesso, um conflito, saldo quatro.

| Execução | Proteção |
| --- | --- |
| Segunda chamada começa depois da primeira | `conferirVersao` detecta versão antiga antes de gravar. |
| As transações se sobrepõem | UPDATE com versão antiga afeta zero linhas; JPA provoca conflito e rollback. |

O teste usa profile concorrencia e H2 isolado, sem transação única envolvendo o
método de teste: os dados precisam estar confirmados para ambas as conexões.
Cada tarefa prepara e limpa `SecurityContextHolder`, pois autenticação é
thread-local. Em requisições web, os filtros do Spring fazem esse trabalho.

## Versão do lote e balanço

`BaseModel.version` usa `@Version`. O SQL de atualização condiciona a gravação à
versão lida e a incrementa ao confirmar. O tratamento global responde 409 ao
conflito de concorrência. Não repetir automaticamente uma saída com versão nova,
pois o usuário precisa rever o saldo antes de decidir.

Balanços acrescentam lock pessimista no pai antes de consultar os itens. Assim,
contagem, conclusão e ajustes validam estado dentro da mesma serialização. A
consulta de lock não contém joins; o grafo necessário é carregado depois.

- Contagem informa versão do item.
- Início e ajustes informam versão do balanço.
- Conclusão informa versão do balanço e versões de todos os itens.
- Concluído rejeita novas contagens; ajustes permanecem idempotentes.

Isso protege o backend mesmo quando o celular demora para receber a atualização.
A interface preserva rascunhos e sinaliza conflitos em vez de trocar suas versões
silenciosamente. Detalhes em [CACHE_E_SINCRONIZACAO.md](CACHE_E_SINCRONIZACAO.md).

## Threads de SSE

`SincronizacaoService` usa ThreadPoolExecutor com duas threads daemon e fila
limitada a 256 tarefas. Os envios não bloqueiam a transação de estoque.
Os eventos de mudança são recebidos depois do commit e filtrados por restaurante.
Antes de enviar, o serviço valida a identidade/sessão novamente; não depende de
um SecurityContextHolder herdado implicitamente pela thread de envio.

Heartbeat ocorre a cada dez segundos. SseEmitter tem prazo de um minuto;
o navegador reconecta. Logout/revogação encerram acesso. Saturação da fila
encerra a conexão afetada, e polling/reconexão recuperam o estado.
Essa distribuição é em memória de uma instância, sem replay durável.

## OCR e backup

OCR de fotos limita uma leitura por instância e o prazo da nota. Backup limita
uma geração por instância com ReentrantLock e cinco minutos. Cada operação
encerra processo/arquivos temporários ao falhar. Essas proteções não equivalem
à exclusão mútua distribuída entre servidores.

## Validação

```bash
mvn -f backend/pom.xml test -Dtest=ConcorrenciaIntegrationTest
mvn -f backend/pom.xml test -Dtest=SincronizacaoBalancoIntegrationTest
```

O segundo usa PostgreSQL temporário e testa versões, isolamento, rollback,
revogação, SSE e volume de consultas. O primeiro verifica o Singleton e conflito
real de consumo. Não inferir proteção completa apenas pelo Singleton ou por um
teste em H2: locks e SQL de produção também são validados em PostgreSQL.
