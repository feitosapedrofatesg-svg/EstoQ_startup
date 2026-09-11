# EstoQ_Startup — Threads, Singleton e concorrência

Local: `/home/pedro/EstoQ_Startup` · Código: `backend/src/main/java/com/estoq/patterns` e `backend/src/test/java/com/estoq/ConcorrenciaIntegrationTest.java`.

Este doc explica, na prática, o uso de **threads** no projeto: o teste de concorrência real que valida a **RN24** (`@Version`, lock otimista) usando um **Singleton thread-safe** com **double-checked locking**, `CountDownLatch`, `ExecutorService` e `SecurityContextHolder` por thread.

---

## 1. Visão geral do teste

O teste `singletonComThreadsRegistraSomenteUmConsumo` dispara **2 threads** consumindo **1 kg cada** do **mesmo lote** (5 kg), ao mesmo tempo, com o **mesmo `versionLote`**.

Resultado esperado (sempre determinístico):

- Exatamente **1 thread vence** e consome 1 kg.
- A outra recebe um **conflito** e seu consumo é descartado (rollback).
- Saldo final do lote: **4 kg**.

| Cenário                 | Quem era a versão ao ler                                     | Resultado                                                        |
| ----------------------- | ------------------------------------------------------------ | ---------------------------------------------------------------- |
| Threads serializam      | A segunda lê `version` já alterada ≠ `versionLote` enviado   | `ConflictException` em `BaixaLoteService.conferirVersao`          |
| Threads se sobrepõem    | As duas leem `version` igual e o UPDATE da segunda afeta 0 linhas | `jakarta.persistence.OptimisticLockException` no flush/commit |

Nos dois casos: 1 sucesso + 1 conflito — e o `GlobalExceptionHandler` mapeia a exceção de concorrência para **409 Conflict**.

---

## 2. Por que duas transações distintas?

O `ConsumoService.registrarConsumo` é `@Transactional`. Cada chamada ao proxy do Spring abre **sua própria transação** (e, por baixo, **sua própria conexão**), mesmo sendo feita por threads diferentes na mesma JVM.

Consequência importante para o teste:

- As classes de teste existentes são `@Transactional` no nível da classe — os dados criados ficam **invisíveis** para outras conexões (não foi feito commit).
- O teste de concorrência **não** usa `@Transactional` na classe: cada `entradas.registrarEntrada` faz commit no final e fica visível para as duas threads. Por isso ele usa um **banco isolado** (`estoq_concorrencia`, perfil `concorrencia`) e não suja o `estoq_test` compartilhado.

---

## 3. O Singleton thread-safe (`RegistradorConsumoSingleton`)

```java
public final class RegistradorConsumoSingleton {

    private static volatile RegistradorConsumoSingleton instancia;

    private final ConsumoService consumos;

    private RegistradorConsumoSingleton(ConsumoService consumos) {
        this.consumos = consumos;
    }

    public static RegistradorConsumoSingleton obter(ConsumoService consumos) {
        var atual = instancia;
        if (atual != null) {          // 1º check fora do lock
            return atual;
        }
        synchronized (RegistradorConsumoSingleton.class) {
            if (instancia == null) {  // 2º check dentro do lock
                instancia = new RegistradorConsumoSingleton(consumos);
            }
            return instancia;
        }
    }

    public MovimentacaoResultadoDTO registrar(ConsumoRequestDTO dto) {
        return consumos.registrarConsumo(dto);
    }
}
```

Pontos estudados no padrão:

- **`volatile`** — impede o *reordering* de memória: a publicação do objeto é visível para todas as threads (sem isso outra thread poderia ver `instancia != null` antes do construtor terminar).
- **Double-checked locking** — na maioria das chamadas nem entra no `synchronized` (custo baixo); o lock só protege a **primeira** criação.
- **Campo efetivamente imutável** — `consumos` é `final`, então o Singleton pode ser compartilhado livremente entre threads.
- **Construtor privado** — a única forma de obter a instância é `obter(...)`; o teste confirma que é sempre a mesma com `assertSame`.

---

## 4. Abrindo as threads

O código mais simples possível: **duas `Thread`** + `join()`, e contadores thread-safe `AtomicInteger`.

```java
var sucessos = new AtomicInteger();
var conflitos = new AtomicInteger();
Runnable consumir = () -> {
    SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("concorrencia@test.local", null, List.of()));
    try {
        singleton.registrar(new ConsumoRequestDTO(produtoId, loteId, n("1"), version, null));
        sucessos.incrementAndGet();
    } catch (ConflictException | OptimisticLockingFailureException | OptimisticLockException e) {
        conflitos.incrementAndGet();
    } finally {
        SecurityContextHolder.clearContext();
    }
};
var t1 = new Thread(consumir);
var t2 = new Thread(consumir);
t1.start();
t2.start();
t1.join();
t2.join();

assertEquals(1, sucessos.get());
assertEquals(1, conflitos.get());
```

- **`new Thread(...).start()`** — cria e dispara cada thread; `TarefaJoin.join()` faz a thread principal esperar as duas terminarem antes de avaliar o resultado.
- **`Runnable` + lambdas** — mesmo código de consumo nas 2 threads, sem `ExecutorService`, `CountDownLatch` nem `Future` (o mais enxuto).
- **`AtomicInteger`** — contadores atômicos (mais seguros que `int` para threads); o conflito deixa o `@Version`/`conferirVersao` fazer o trabalho e é só contado.

O invariante segue garantido mesmo sem barreira de largada: se as threads se sobrepõem, o perdedor colide no flush (`OptimisticLockException`); se serializam, o segundo recebe `ConflictException` por versão antiga.

---

## 5. Segurança por thread (`SecurityContextHolder`)

O Spring Security guarda a autenticação em **thread-local**: cada thread tem o "seu" contexto. Como `UsuarioAtual.obter()` lê esse contexto, cada tarefa precisa configurar o seu antes de chamar o serviço:

```java
SecurityContextHolder.getContext().setAuthentication(...); // dentro da thread da tarefa
try { /* trabalho */ } finally { SecurityContextHolder.clearContext(); }
```

Em aplicações web normais quem faz isso é o `SecurityContextHolderFilter` (por requisição). Em threads que você cria manualmente, **você** é responsável por propagar e limpar.

---

## 6. O lock otimista (`@Version`)

`BaseModel` carrega a versão:

```java
@Version
@Column(nullable = false)
private Long version;
```

Hibernate transforma todo `UPDATE` em `... where id=? and version=?`, incrementando a versão a cada escrita. Quando duas transações tentam atualizar o mesmo lote:

- A **primeira** a commitar vence (version 0 → 1).
- A **segunda** executa o UPDATE, **0 linhas** alteradas ("Unexpected row count") → `jakarta.persistence.OptimisticLockException` → rollback automático.

Além disso, `BaixaLoteService.conferirVersao` valida a versão **antes** de escrever para quem informa `versionLote` no request:

```java
public void conferirVersao(LoteModel lote, Long version) {
    if (version != null && !Objects.equals(version, lote.getVersion())) {
        throw new ConflictException();
    }
}
```

E o `GlobalExceptionHandler` transforma a falha técnica em algo amigável:

```java
@ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
public ResponseEntity<ErrorResponse> concorrencia(Exception ex) {
    return negocio(new ConflictException());
}   // → HTTP 409 Conflict
```

---

## 7. Como rodar

```bash
cd backend
mvn -o test   # 27 testes verdes (0 falhas)
```

Filtrando só o teste de concorrência:

```bash
mvn -o test -Dtest=ConcorrenciaIntegrationTest
```

---

## 8. Arquivos relacionados

- `backend/src/main/java/com/estoq/patterns/RegistradorConsumoSingleton.java` — Singleton (double-checked locking).
- `backend/src/test/java/com/estoq/ConcorrenciaIntegrationTest.java` — teste com threads.
- `backend/src/test/resources/application-concorrencia.properties` — banco H2 isolado (`estoq_concorrencia`).
- `backend/src/main/java/com/estoq/business/lotes/BaixaLoteService.java` — validação de versão.
- `backend/src/main/java/com/estoq/core/domains/BaseModel.java` — campo `@Version`.
- `backend/src/main/java/com/estoq/core/exceptions/GlobalExceptionHandler.java` — 409 do lock otimista.
- `backend/src/main/java/com/estoq/business/auth/UsuarioAtual.java` — lê autenticação do `SecurityContextHolder`.