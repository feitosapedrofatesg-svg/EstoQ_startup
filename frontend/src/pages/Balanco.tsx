import { useState } from "react";
import { useAuth } from "../store/auth";
import { useFetch } from "../lib/hooks";
import { api } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  Modal,
  Field,
  Input,
  Select,
  DataTable,
  Badge,
  StatusBadge,
  EmptyState,
} from "../components/UI";
import { fmtDateTime, fmtNum, parseDecimal } from "../lib/format";
import type {
  BalancoDTO,
  ConfiguracaoBalancoDTO,
  PeriodicidadeBalanco,
  TipoBalanco,
} from "../lib/types";
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

export function Balanco() {
  const { isAdmin, canMove } = useAuth();
  const toast = useToast();
  const { data, loading, error, refresh } = useFetch<BalancoDTO[]>("/api/balancos");
  const { data: configs, refresh: refreshConfig } = useFetch<ConfiguracaoBalancoDTO[]>(
    isAdmin ? "/api/configuracoes-balanco" : null
  );
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState<number | null>(null); // balanco id em ação
  const [busyLabel, setBusyLabel] = useState("");

  const doAction = async (id: number, label: string, fn: () => Promise<unknown>) => {
    setBusy(id);
    setBusyLabel(label);
    try {
      await fn();
      toast.success(label);
      void refresh();
    } catch (e) {
      toast.error(label, (e as Error).message);
    } finally {
      setBusy(null);
      setBusyLabel("");
    }
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

      {error && <div className="alertbanner alertbanner--bad">{error}</div>}

      {isAdmin && configs && (
        <ConfigBalanco config={configs[0] ?? null} onDone={() => void refreshConfig()} />
      )}

      <Card title="Balanços">
        {loading && !data && <p className="muted">Carregando…</p>}
        {data && data.length === 0 ? (
          <EmptyState
            title="Nenhum balanço realizado"
            text="Crie um balanço para conferir o estoque físico contra o sistema."
            action={
              canMove ? (
                <Button variant="outline" onClick={() => setShowNew(true)}>
                  Fazer o primeiro
                </Button>
              ) : (
                <p className="muted">A Cozinha inicia balanços; a confirmação é do administrador.</p>
              )
            }
          />
        ) : (
          <div className="balanco-list">
            {data?.map((b) => {
              const total = b.itens.length;
              const counted = b.itens.filter((i) => i.quantidadeFisica !== null).length;
              const pct = total ? Math.round((counted / total) * 100) : 0;
              const needConfirm =
                b.status === "EM_ANDAMENTO" && isAdmin && counted === total && total > 0;
              const canAjustar = b.status === "CONCLUIDO" && isAdmin && b.itens.some((i) => !i.ajusteAplicado);
              return (
                <article key={b.id} className="balanco">
                  <header className="balanco__head">
                    <div>
                      <h3 className="balanco__title">
                        Balanço #{b.id}
                        <StatusBadge tone={STATUS_TONE[b.status]}>{STATUS_LABEL[b.status]}</StatusBadge>
                      </h3>
                      <p className="balanco__meta">
                        {fmtDateTime(b.dataHora)} · {b.tipo} · por {b.usuarioNome}
                      </p>
                    </div>
                    <div className="balanco__actions">
                      {b.status === "PENDENTE" && canMove && (
                        <Button
                          size="sm"
                          icon="clipboard-list"
                          loading={busy === b.id && busyLabel === "Balanço iniciado"}
                          onClick={() =>
                            void doAction(b.id, "Balanço iniciado", () =>
                              api.post(`/api/balancos/${b.id}/iniciar`)
                            )
                          }
                        >
                          Iniciar contagem
                        </Button>
                      )}
                      {needConfirm && (
                        <Button
                          size="sm"
                          variant="ink"
                          loading={busy === b.id && busyLabel === "Balanço confirmado"}
                          onClick={() =>
                            void doAction(b.id, "Balanço confirmado", () =>
                              api.post(`/api/balancos/${b.id}/confirmar`)
                            )
                          }
                        >
                          Confirmar balanço
                        </Button>
                      )}
                      {canAjustar && (
                        <Button
                          size="sm"
                          variant="danger"
                          loading={busy === b.id && busyLabel === "Ajustes aplicados"}
                          onClick={() =>
                            void doAction(b.id, "Ajustes aplicados", () =>
                              api.post(`/api/balancos/${b.id}/gerar-ajustes`)
                            )
                          }
                        >
                          Gerar ajustes de estoque
                        </Button>
                      )}
                    </div>
                  </header>

                  {total > 0 && (
                    <>
                      <div className="progress" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={`${pct}% dos itens contados`}>
                        <div className="progress__fill" style={{ width: `${pct}%` }} />
                      </div>
                      <p className="balanco__count">
                        {counted} de {total} itens contados
                      </p>
                      {b.status === "EM_ANDAMENTO" && (
                        <ItensContagem
                          balanco={b}
                          onItem={(id, q) =>
                            void doAction(b.id, "Contagem salva", () =>
                              api.put(`/api/balancos/${b.id}/itens/${id}/contagem`, {
                                quantidadeFisica: q,
                              })
                            )
                          }
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
                    </>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </Card>

      {showNew && (
        <NovoBalanco
          onClose={() => setShowNew(false)}
          onDone={() => {
            setShowNew(false);
            toast.success("Balanço criado");
            void refresh();
          }}
        />
      )}
    </>
  );
}

function NovoBalanco({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [tipo, setTipo] = useState<TipoBalanco>("GERAL");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await api.post("/api/balancos", { tipo });
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Novo balanço"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button onClick={() => void submit()} loading={submitting}>
            Criar balanço
          </Button>
        </>
      }
    >
      <Field label="Abrangência" htmlFor="bal-tipo">
        <Select id="bal-tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoBalanco)}>
          <option value="GERAL">Geral — todos os produtos ativos</option>
          <option value="PARCIAL">Parcial — você escolhe depois</option>
        </Select>
      </Field>
      <p className="muted" style={{ marginTop: 8 }}>
        Depois de criar, use "Iniciar contagem" para gerar a lista de itens.
      </p>
      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          {error}
        </div>
      )}
    </Modal>
  );
}

function ItensContagem({
  balanco,
  onItem,
  busy,
}: {
  balanco: BalancoDTO;
  onItem: (itemId: number, quantidadeFisica: number) => void;
  busy: boolean;
}) {
  const [vals, setVals] = useState<Record<number, string>>(() => {
    const init: Record<number, string> = {};
    for (const i of balanco.itens) init[i.id] = i.quantidadeFisica === null ? "" : String(i.quantidadeFisica);
    return init;
  });

  return (
    <div className="contagem">
      <div className="contagem__bar">
        <span className="contagem__label">Contagem</span>
      </div>
      <DataTable
        caption={`Contagem do balanço ${balanco.id}`}
        headers={["Produto", "Sistema", "Contagem física", ""]}
      >
        {balanco.itens.map((i) => {
          const n = parseDecimal(vals[i.id]);
          const valid = n !== null && n >= 0;
          return (
            <tr key={i.id}>
              <td>
                <strong>{i.produtoNome}</strong>
              </td>
              <td>
                {fmtNum(i.quantidadeSistema)} {i.unidadeMedida.toLowerCase()}
              </td>
              <td>
                <div className="contagem__input">
                  <input
                    className="input"
                    inputMode="decimal"
                    aria-label={`Contagem de ${i.produtoNome}`}
                    value={vals[i.id]}
                    onChange={(e) => setVals((p) => ({ ...p, [i.id]: e.target.value }))}
                    placeholder="0"
                  />
                  <span className="contagem__unit">{i.unidadeMedida.toLowerCase()}</span>
                </div>
              </td>
              <td>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={!valid || busy}
                  onClick={() => valid && onItem(i.id, n)}
                >
                  Salvar
                </Button>
              </td>
            </tr>
          );
        })}
      </DataTable>
    </div>
  );
}

function ConfigBalanco({
  config,
  onDone,
}: {
  config: ConfiguracaoBalancoDTO | null;
  onDone: () => void;
}) {
  const toast = useToast();
  const [periodicidade, setPeriodicidade] = useState<PeriodicidadeBalanco>(
    config?.periodicidade ?? "SEMANAL"
  );
  const [dia, setDia] = useState<string>(
    config?.diaExecucao === null || config?.diaExecucao === undefined ? "" : String(config.diaExecucao)
  );
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    const diaNum = dia ? parseInt(dia, 10) : null;
    const body = {
      periodicidade,
      diaExecucao: periodicidade === "DIARIA" ? null : diaNum,
      proximaExecucao: null,
      ...(config ? { id: config.id, version: config.version } : {}),
    };
    try {
      if (config) {
        await api.put(`/api/configuracoes-balanco/${config.id}`, body);
        toast.success("Agendamento atualizado");
      } else {
        await api.post("/api/configuracoes-balanco", body);
        toast.success("Agendamento criado");
      }
      onDone();
    } catch (e) {
      toast.error("Não foi possível salvar", (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const help =
    periodicidade === "DIARIA"
      ? "Todos os dias."
      : periodicidade === "SEMANAL"
        ? "Dia da semana (1 = segunda, 7 = domingo)."
        : "Dia do mês (1 a 28).";

  return (
    <Card title="Agendamento do balanço" bodyClassName="config-form">
      <div className="form-grid-3">
        <Field label="Frequência">
          <Select value={periodicidade} onChange={(e) => setPeriodicidade(e.target.value as PeriodicidadeBalanco)}>
            <option value="DIARIA">Diária</option>
            <option value="SEMANAL">Semanal</option>
            <option value="MENSAL">Mensal</option>
          </Select>
        </Field>
        <Field label="Dia (quando não é diário)" hint={help}>
          <Input
            type="number"
            min={periodicidade === "SEMANAL" ? 1 : 1}
            max={periodicidade === "SEMANAL" ? 7 : 28}
            value={dia}
            disabled={periodicidade === "DIARIA"}
            onChange={(e) => setDia(e.target.value)}
          />
        </Field>
        <div className="config-form__btn">
          <Button onClick={() => void save()} loading={saving} variant="outline">
            {config ? "Salvar agendamento" : "Criar agendamento"}
          </Button>
        </div>
      </div>
    </Card>
  );
}