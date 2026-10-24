import { mensagemErro } from "../lib/api";
import { imprimirBalanco } from "../components/balanco/ImprimirBalanco";
import { ProximoBalanco } from "../components/ProximoBalanco";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "../store/auth";
import { useFetch, updateCache } from "../lib/hooks";
import { ApiError, api } from "../lib/api";
import { PageHeader, Card, Button, ListToggle, DataTable, Badge, StatusBadge, EmptyState, Confirm } from "../components/UI";
import { fmtDateTime, fmtNum } from "../lib/format";
import type { BalancoDTO, ConfiguracaoBalancoDTO, ItemBalancoDTO } from "../lib/types";
import { useToast } from "../store/toast";

const STATUS_TONE: Record<BalancoDTO["status"], "info" | "warn" | "good" | "neutral"> = {
  PENDENTE: "neutral",
  EM_ANDAMENTO: "warn",
  CONCLUIDO: "good",
};

const STATUS_LABEL: Record<BalancoDTO["status"], string> = {
  PENDENTE: "Aguardando início",
  EM_ANDAMENTO: "Em contagem",
  CONCLUIDO: "Concluído",
};


import { NovoBalanco } from "../components/balanco/NovoBalanco";
import { ItensContagem } from "../components/balanco/ItensContagem";
import { ConfigBalanco } from "../components/balanco/ConfigBalanco";

export function Balanco() {
  const { isAdmin, canMove } = useAuth();
  const toast = useToast();
  const { data, loading, error, refresh, setData } = useFetch<BalancoDTO[]>("/api/balancos");
  const { data: configs, refresh: refreshConfig } = useFetch<ConfiguracaoBalancoDTO[]>(
    isAdmin ? "/api/configuracoes-balanco" : null
  );
  const [agendaRevision, setAgendaRevision] = useState(0);
  const [showNew, setShowNew] = useState(false);
  const [aceitarSistema, setAceitarSistema] = useState<BalancoDTO | null>(null);
  const [confirmarVazio, setConfirmarVazio] = useState<BalancoDTO | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [busyLabel, setBusyLabel] = useState("");
  const [rascunhos, setRascunhos] = useState<Record<number, boolean>>({});
  const statusAnteriores = useRef<Record<number, string>>({});
  useEffect(() => {
    for (const b of data ?? []) {
      if (statusAnteriores.current[b.id] === "EM_ANDAMENTO" && b.status === "CONCLUIDO" && rascunhos[b.id])
        toast.warning("Balanço concluído", "As contagens que você estava digitando não foram salvas.");
      statusAnteriores.current[b.id] = b.status;
    }
  }, [data]);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  const doAction = async (id: number, label: string, fn: () => Promise<BalancoDTO>) => {
    setBusy(id);
    setBusyLabel(label);
    try {
      const atualizado = await fn();
      toast.success(label);
      void refreshConfig();
      setAgendaRevision(v => v + 1);
      // O backend devolve o balanço completo já atualizado: mescla na lista
      // (sem re-buscar /api/balancos inteiro a cada clique) e mantém o cache SWR coerente.
      const merge = (prev: BalancoDTO[]) => prev.map((b) => {
        if (b.id !== atualizado.id || b.version > atualizado.version) return b;
        return { ...atualizado, itens: atualizado.itens.map(item => {
          const recente = b.itens.find(i => i.id === item.id);
          return recente && recente.version > item.version ? recente : item;
        }) };
      });
      setData((prev) => (prev ? merge(prev) : prev));
      updateCache<BalancoDTO[]>("/api/balancos", merge);
    } catch (e) {
      toast.error("Operação não concluída", mensagemErro(e));
      if (e instanceof ApiError && e.status === 409) await refresh();
    } finally {
      setBusy(null);
      setBusyLabel("");
    }
  };

  const salvarContagem = async (balancoId: number, itemId: number, quantidadeFisica: number, version: number): Promise<ItemBalancoDTO | null> => {
    const itemAnterior = data?.find((b) => b.id === balancoId)?.itens.find((item) => item.id === itemId);
    if (!itemAnterior) return null;

    const mesclarItem = (itemAtualizado: ItemBalancoDTO) => {
      const merge = (prev: BalancoDTO[]) => prev.map((b) =>
        b.id === balancoId
          ? { ...b, itens: b.itens.map((item) => item.id === itemAtualizado.id && item.version <= itemAtualizado.version ? itemAtualizado : item) }
          : b
      );
      setData((prev) => (prev ? merge(prev) : prev));
      updateCache<BalancoDTO[]>("/api/balancos", merge);
    };

    setBusy(balancoId);
    setBusyLabel("Contagem salva");
    try {
      const itemAtualizado = await api.put<ItemBalancoDTO>(
        `/api/balancos/${balancoId}/itens/${itemId}/contagem`,
        { quantidadeFisica, version }
      );
      mesclarItem(itemAtualizado);
      toast.success("Contagem salva");
      return itemAtualizado;
    } catch (e) {
      toast.error("Contagem não salva", mensagemErro(e));
      await refresh();
      return null;
    } finally {
      setBusy(null);
      setBusyLabel("");
    }
  };

  const aceitarSaldoSistema = async (balanco: BalancoDTO) => {
    if (rascunhos[balanco.id]) {
      toast.warning("Salve as contagens em edição", "Há valores digitados que ainda não foram confirmados.");
      setAceitarSistema(null);
      return;
    }
    await doAction(balanco.id, "Saldo do sistema aceito", () =>
      api.post<BalancoDTO>(`/api/balancos/${balanco.id}/contagens/sistema`, { version: balanco.version })
    );
    setAceitarSistema(null);
  };

  const pendentesAceitar = aceitarSistema?.itens.filter((i) => i.quantidadeFisica === null).length ?? 0;

  const confirmarBalanco = async (balanco: BalancoDTO) => {
    await doAction(balanco.id, "Balanço confirmado", () =>
      api.post<BalancoDTO>(`/api/balancos/${balanco.id}/confirmar`, {
        version: balanco.version,
        itens: balanco.itens.map(i => ({ id: i.id, version: i.version })),
      })
    );
    setConfirmarVazio(null);
  };

  return (
    <>
      <PageHeader
        title="Balanço físico"
        subtitle="Conte o que tem de fato e deixe o sistema ajustar seu estoque com precisão."
        actions={
          canMove && (
            <Button icon="scale" onClick={() => setShowNew(true)}>
              Novo balanço
            </Button>
          )
        }
      />

      <ProximoBalanco revision={agendaRevision} />

      {error && <div className="alertbanner alertbanner--bad">{error}</div>}

      {isAdmin && configs && (
        <ConfigBalanco config={configs[0] ?? null} onDone={() => { void refreshConfig(); setAgendaRevision(v => v + 1); }} />
      )}

      <Card title="Balanços">
        {loading && !data ? (
          <p className="muted">Carregando…</p>
        ) : data && data.length === 0 ? (
          <EmptyState
            title="Nenhum balanço realizado"
            text="Crie um balanço para conferir o estoque físico contra o sistema."
            action={
              canMove ? (
                <Button variant="outline" onClick={() => setShowNew(true)}>
                  Fazer o primeiro
                </Button>
              ) : (
                <p className="muted">O perfil Operacional inicia balanços; a confirmação é do administrador.</p>
              )
            }
          />
        ) : (
          <div className="balanco-list">
            {data?.map((b) => {
              const total = b.itens.length;
              const counted = b.itens.filter((i) => i.quantidadeFisica !== null).length;
              const pendentes = total - counted;
              const pct = total ? Math.round((counted / total) * 100) : 0;
              const needConfirm =
                b.status === "EM_ANDAMENTO" && isAdmin && counted === total;
              const canAjustar = b.status === "CONCLUIDO" && isAdmin && b.itens.some((i) => !i.ajusteAplicado);
              return (
                <article key={b.id} className={`balanco ${!expanded.has(b.id) ? "balanco--collapsed" : ""}`}>
                  <header className="balanco__head">
                    <div className="balanco__head-main">
                      <ListToggle
                        expanded={expanded.has(b.id)}
                        controls={`balanco-itens-${b.id}`}
                        label={`balanço #${b.id}`}
                        subject="balanço"
                        onToggle={() => setExpanded((prev) => {
                          const next = new Set(prev);
                          next.has(b.id) ? next.delete(b.id) : next.add(b.id);
                          return next;
                        })}
                      />
                      <div>
                        <h3 className="balanco__title">
                          Balanço #{b.id}
                          <StatusBadge tone={STATUS_TONE[b.status]}>{STATUS_LABEL[b.status]}</StatusBadge>
                          {!expanded.has(b.id) && (
                            <span className="balanco__summary">
                              {counted}/{total} itens contados
                            </span>
                          )}
                        </h3>
                        <p className="balanco__meta">
                          {fmtDateTime(b.dataHora)} · {b.tipo} · por {b.usuarioNome}
                        </p>
                        {b.tipo === "PARCIAL" && (b.categoriaNomes?.length ?? 0) > 0 && (
                          <div className="balanco__cats">
                            {b.categoriaNomes.map((n) => (
                              <Badge key={n} tone="info">{n}</Badge>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="balanco__actions">
                      <Button
                        size="sm"
                        variant="ghost"
                        icon="printer"
                        aria-label={`Imprimir balanço ${b.id}`}
                        onClick={() => {
                          try { imprimirBalanco(b); }
                          catch (erro) { toast.error("Não foi possível imprimir", (erro as Error).message); }
                        }}
                      />
                      {b.status === "PENDENTE" && canMove && (
                        <Button
                          size="sm"
                          icon="clipboard-list"
                          loading={busy === b.id && busyLabel === "Balanço iniciado"}
                          onClick={() =>
                            void doAction(b.id, "Balanço iniciado", () =>
                              api.post<BalancoDTO>(`/api/balancos/${b.id}/iniciar`, { version: b.version })
                            )
                          }
                        >
                          Iniciar contagem
                        </Button>
                      )}
                      {b.status === "EM_ANDAMENTO" && canMove && pendentes > 0 && (
                        <Button
                          size="sm"
                          variant="outline"
                          icon="refresh"
                          loading={busy === b.id && busyLabel === "Saldo do sistema aceito"}
                          onClick={() => {
                            if (rascunhos[b.id]) {
                              toast.warning("Salve as contagens em edição",
                                "Há valores digitados que ainda não foram confirmados.");
                              return;
                            }
                            setAceitarSistema(b);
                          }}
                        >
                          Aceitar saldo do sistema
                        </Button>
                      )}
                      {needConfirm && (
                        <Button
                          size="sm"
                          variant="ink"
                          loading={busy === b.id && busyLabel === "Balanço confirmado"}
                          onClick={() => {
                            if (total === 0) setConfirmarVazio(b);
                            else void confirmarBalanco(b);
                          }}
                        >
                          {total === 0 ? "Finalizar sem itens" : "Confirmar balanço"}
                        </Button>
                      )}
                      {canAjustar && (
                        <Button
                          size="sm"
                          variant="danger"
                          loading={busy === b.id && busyLabel === "Ajustes aplicados"}
                          onClick={() =>
                            void doAction(b.id, "Ajustes aplicados", () =>
                              api.post<BalancoDTO>(`/api/balancos/${b.id}/gerar-ajustes`, { version: b.version })
                            )
                          }
                        >
                          Gerar ajustes de estoque
                        </Button>
                      )}
                    </div>
                  </header>

                  {total === 0 && b.status !== "PENDENTE" && (
                    <div id={`balanco-itens-${b.id}`} hidden={!expanded.has(b.id)}>
                      <p className="balanco__count">
                        {b.status === "CONCLUIDO"
                          ? "Balanço concluído sem itens."
                          : "Este balanço tem 0 itens. O administrador pode finalizá-lo sem alterar o estoque."}
                      </p>
                    </div>
                  )}
                  {total > 0 && (
                    <div id={`balanco-itens-${b.id}`} hidden={!expanded.has(b.id)}>
                      <div className="progress" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={`${pct}% dos itens contados`}>
                        <div className="progress__fill" style={{ width: `${pct}%` }} />
                      </div>
                      <p className="balanco__count">
                        {counted} de {total} itens contados
                        {counted < total && (
                          <span className="muted"> — os campos mostram o saldo do sistema, mas ainda não foram confirmados.</span>
                        )}
                      </p>
                      {b.status === "EM_ANDAMENTO" && (
                        <ItensContagem
                          balanco={b}
                          onItem={(id, q, version) => salvarContagem(b.id, id, q, version)}
                          onDirty={dirty => setRascunhos(prev => ({ ...prev, [b.id]: dirty }))}
                          busy={busy === b.id}
                        />
                      )}
                      {b.status !== "EM_ANDAMENTO" && (
                        <DataTable
                          caption={`Itens do balanço ${b.id}`}
                          headers={["Produto", "Sistema", "Contado", "Diferença", "Ajustado"]}
                        >
                          {b.itens.map((i) => (
                            <tr key={i.id}>
                              <td>
                                <strong>{i.produtoNome}</strong>
                              </td>
                              <td>
                                {fmtNum(i.quantidadeSistema)} {i.unidadeMedida.toLowerCase()}
                              </td>
                              <td>
                                {i.quantidadeFisica === null
                                  ? "—"
                                  : `${fmtNum(i.quantidadeFisica)} ${i.unidadeMedida.toLowerCase()}`}
                              </td>
                              <td className={i.diferenca !== null && i.diferenca < 0 ? "text-bad" : "text-good"}>
                                {i.diferenca === null ? "—" : `${i.diferenca > 0 ? "+" : ""}${fmtNum(i.diferenca)}`}
                              </td>
                              <td>
                                {i.ajusteAplicado ? (
                                  <StatusBadge label="Ajustado" tone="good" />
                                ) : (
                                  <Badge tone="neutral">Pendente</Badge>
                                )}
                              </td>
                            </tr>
                          ))}
                        </DataTable>
                      )}
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </Card>

      <Confirm
        open={confirmarVazio !== null}
        title="Finalizar balanço sem itens?"
        message="Este balanço tem 0 itens. Ele será marcado como concluído, sem criar contagens nem alterar o estoque."
        confirmLabel="Finalizar sem itens"
        loading={busy === confirmarVazio?.id && busyLabel === "Balanço confirmado"}
        onConfirm={() => {
          if (confirmarVazio) void confirmarBalanco(confirmarVazio);
        }}
        onClose={() => {
          if (busy === null) setConfirmarVazio(null);
        }}
      />

      <Confirm
        open={aceitarSistema !== null}
        title="Aceitar saldo do sistema?"
        message={
          aceitarSistema ? (
            <>
              <p>
                <strong>{pendentesAceitar} {pendentesAceitar === 1 ? "item" : "itens"}</strong>{" "}
                {pendentesAceitar === 1 ? "será preenchido" : "serão preenchidos"} com o saldo atual do
                sistema. Em um balanço zerado, isso registra 0 nos itens pendentes.
              </p>
              <p className="muted">
                Itens já contados não serão alterados; as contagens salvas permanecem como estão.
              </p>
            </>
          ) : null
        }
        confirmLabel="Aceitar saldo"
        loading={busy === aceitarSistema?.id && busyLabel === "Saldo do sistema aceito"}
        onConfirm={() => {
          if (aceitarSistema) void aceitarSaldoSistema(aceitarSistema);
        }}
        onClose={() => {
          if (!busy) setAceitarSistema(null);
        }}
      />

      {showNew && (
        <NovoBalanco
          onClose={() => setShowNew(false)}
          onDone={(criado) => {
            setShowNew(false);
            const inserir = (prev: BalancoDTO[]) => [criado, ...prev.filter((b) => b.id !== criado.id)];
            setData((prev) => inserir(prev ?? []));
            updateCache<BalancoDTO[]>("/api/balancos", inserir);
            toast.success("Balanço criado");
            void refresh();
          }}
        />
      )}
    </>
  );
}
