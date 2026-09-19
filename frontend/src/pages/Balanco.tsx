import { useEffect, useState } from "react";
import { useAuth } from "../store/auth";
import { useFetch } from "../lib/hooks";
import { api } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  Modal,
  Field,
  Select,
  DataTable,
  Badge,
  StatusBadge,
  EmptyState,
} from "../components/UI";
import { fmtDateTime, fmtNum } from "../lib/format";
import type {
  BalancoDTO,
  CategoriaDTO,
  ConfiguracaoBalancoDTO,
  Page,
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

function printBalanco(b: BalancoDTO) {
  const counted = b.itens.filter((i) => i.quantidadeFisica !== null).length;
  const escopo = b.tipo === "PARCIAL" && b.categoriaNomes?.length
    ? ` &mdash; categorias: ${b.categoriaNomes.join(", ")}`
    : "";
  const rows = b.itens
    .map((i) => {
      const sys = i.quantidadeSistema !== null ? fmtNum(i.quantidadeSistema) : "—";
      const cnt =
        i.quantidadeFisica !== null
          ? `${fmtNum(i.quantidadeFisica)} ${i.unidadeMedida.toLowerCase()}`
          : "—";
      const diff =
        i.diferenca !== null
          ? `${i.diferenca > 0 ? "+" : ""}${fmtNum(i.diferenca)} ${i.unidadeMedida.toLowerCase()}`
          : "—";
      const adj = b.status === "CONCLUIDO" ? (i.ajusteAplicado ? "Ajustado" : "Pendente") : "";
      return `<tr>
        <td>${i.produtoNome}</td>
        <td>${sys} ${i.unidadeMedida.toLowerCase()}</td>
        <td>${cnt}</td>
        <td>${diff}</td>
        ${adj !== "" ? `<td>${adj}</td>` : ""}
      </tr>`;
    })
    .join("");

  const html = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8"><title>Balanço #${b.id}</title>
<style>
  *{margin:0;padding:0;box-sizing:border-box}
  body{font-family:system-ui,-apple-system,sans-serif;color:#1a1a2e;padding:24px}
  h1{font-size:20px;margin-bottom:4px}
  .meta{color:#64748b;font-size:13px;margin-bottom:18px}
  table{width:100%;border-collapse:collapse;font-size:13px}
  th,td{text-align:left;padding:6px 8px;border-bottom:1px solid #e2e8f0}
  th{font-weight:600;color:#475569;font-size:11px;text-transform:uppercase;letter-spacing:.3px}
  td:nth-child(n+2),th:nth-child(n+2){text-align:right}
  .neg{color:#dc2626}.pos{color:#16a34a}
  .footer{margin-top:18px;font-size:11px;color:#94a3b8}
  @media print{body{padding:12px}}
</style></head>
<body>
  <h1>Balanço #${b.id}</h1>
  <p class="meta">${STATUS_LABEL[b.status]} &mdash; ${fmtDateTime(b.dataHora)} &mdash; ${b.tipo}${escopo} &mdash; ${b.usuarioNome}</p>
  <table>
    <thead><tr><th>Produto</th><th>Sistema</th><th>Contado</th><th>Diferença</th>${b.status === "CONCLUIDO" ? "<th>Ajustado</th>" : ""}</tr></thead>
    <tbody>${rows}</tbody>
  </table>
  <p class="footer">${counted} de ${b.itens.length} itens contados &mdash; gerado em ${new Date().toLocaleString("pt-BR")}</p>
</body></html>`;
  const w = window.open("", "_blank", "width=720,height=600");
  if (w) {
    w.document.write(html);
    w.document.close();
    w.focus();
    setTimeout(() => w.print(), 300);
  }
}

export function Balanco() {
  const { isAdmin, canMove } = useAuth();
  const toast = useToast();
  const { data, loading, error, refresh } = useFetch<BalancoDTO[]>("/api/balancos");
  const { data: configs, refresh: refreshConfig } = useFetch<ConfiguracaoBalancoDTO[]>(
    isAdmin ? "/api/configuracoes-balanco" : null
  );
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState<number | null>(null);
  const [busyLabel, setBusyLabel] = useState("");
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set());

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
                <article key={b.id} className={`balanco ${collapsed.has(b.id) ? "balanco--collapsed" : ""}`}>
                  <header className="balanco__head">
                    <div className="balanco__head-main">
                      <button
                        type="button"
                        className="balanco__toggle"
                        aria-label={collapsed.has(b.id) ? "Expandir balanço" : "Minimizar balanço"}
                        onClick={() =>
                          setCollapsed((prev) => {
                            const next = new Set(prev);
                            next.has(b.id) ? next.delete(b.id) : next.add(b.id);
                            return next;
                          })
                        }
                      >
                        <span className={`balanco__chevron${collapsed.has(b.id) ? "" : " balanco__chevron--open"}`} aria-hidden="true">▸</span>
                      </button>
                      <div>
                        <h3 className="balanco__title">
                          Balanço #{b.id}
                          <StatusBadge tone={STATUS_TONE[b.status]}>{STATUS_LABEL[b.status]}</StatusBadge>
                          {collapsed.has(b.id) && (
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
                        onClick={() => printBalanco(b)}
                      />
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

                  {!collapsed.has(b.id) && total > 0 && (
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
  const [cats, setCats] = useState<CategoriaDTO[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let mounted = true;
    api.get<Page<CategoriaDTO>>("/api/categorias?size=100").then((r) => {
      if (mounted) setCats(r.content);
    }).catch(() => {});
    return () => { mounted = false; };
  }, []);

  const toggle = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await api.post("/api/balancos", {
        tipo,
        ...(tipo === "PARCIAL" ? { categorias: [...selected] } : {}),
      });
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
          <Button
            onClick={() => void submit()}
            loading={submitting}
            disabled={tipo === "PARCIAL" && selected.size === 0}
          >
            Criar balanço
          </Button>
        </>
      }
    >
      <Field label="Abrangência" htmlFor="bal-tipo">
        <Select id="bal-tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoBalanco)}>
          <option value="GERAL">Geral — todos os produtos ativos</option>
          <option value="PARCIAL">Parcial — por categoria(s) de alimento</option>
        </Select>
      </Field>
      {tipo === "PARCIAL" && (
        <div className="bal-tipo-cats">
          {cats.length === 0 && <p className="muted" style={{ marginTop: 8 }}>Carregando categorias…</p>}
          {cats.map((c) => (
            <label key={c.id} className="bal-tipo-cat">
              <input
                type="checkbox"
                checked={selected.has(c.id)}
                onChange={() => toggle(c.id)}
              />
              <span>{c.nome}</span>
            </label>
          ))}
          {cats.length > 0 && (
            <p className="muted" style={{ marginTop: 8 }}>
              Selecione uma ou mais categorias. Só os itens dessas categorias entrarão no balanço.
            </p>
          )}
        </div>
      )}
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

function parseContagem(s: string): number | null {
  if (!s.trim()) return null;
  const t = s.trim();
  const hasComma = t.includes(",");
  const hasDot = t.includes(".");
  let n: number;
  if (hasComma && hasDot) {
    n = Number(t.replace(/\./g, "").replace(",", "."));
  } else if (hasComma) {
    n = Number(t.replace(",", "."));
  } else if (hasDot) {
    n = Number(t.replace(",", "."));
  } else {
    n = Number(t);
  }
  return Number.isFinite(n) ? n : null;
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
    for (const i of balanco.itens) {
      init[i.id] =
        i.quantidadeFisica === null
          ? i.quantidadeSistema === null
            ? ""
            : fmtNum(i.quantidadeSistema)
          : fmtNum(i.quantidadeFisica);
    }
    return init;
  });

  const confirmItem = (itemId: number, n: number) => {
    onItem(itemId, n);
  };

  const isConfirmed = (item: typeof balanco.itens[0]) => {
    const saved = item.quantidadeFisica;
    if (saved === null) return false;
    const typed = parseContagem(vals[item.id]);
    return typed !== null && Number(saved) === typed;
  };

  return (
    <div className="contagem">
      <div className="contagem__bar">
        <span className="contagem__label">Contagem</span>
      </div>
      <DataTable
        caption={`Contagem do balanço ${balanco.id}`}
        headers={["Produto", "Sistema", "Contagem física", "Resultado", ""]}
      >
        {balanco.itens.map((i) => {
          const n = parseContagem(vals[i.id]);
          const valid = n !== null && n >= 0;
          const diff = valid && i.quantidadeSistema !== null ? n - i.quantidadeSistema : null;
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
                <span
                  className={
                    diff === null
                      ? "muted"
                      : diff === 0
                        ? "text-good"
                        : diff < 0
                          ? "text-bad"
                          : "text-good"
                  }
                >
                  {diff === null ? "—" : `${diff > 0 ? "+" : ""}${fmtNum(diff)} ${i.unidadeMedida.toLowerCase()}`}
                </span>
              </td>
              <td>
                <Button
                  size="sm"
                  variant={isConfirmed(i) ? "good" : "outline"}
                  disabled={isConfirmed(i) || !valid || busy}
                  icon={isConfirmed(i) ? "check-circle" : undefined}
                  onClick={() => valid && confirmItem(i.id, n)}
                >
                  OK
                </Button>
              </td>
            </tr>
          );
        })}
      </DataTable>
    </div>
  );
}

const WEEKDAY_LABELS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];

const WEEK_DAYS: { label: string; value: number }[] = [
  { label: "Seg", value: 1 },
  { label: "Ter", value: 2 },
  { label: "Qua", value: 3 },
  { label: "Qui", value: 4 },
  { label: "Sex", value: 5 },
  { label: "Sáb", value: 6 },
  { label: "Dom", value: 7 },
];

const MONTH_NAMES = [
  "Janeiro","Fevereiro","Março","Abril","Maio","Junho",
  "Julho","Agosto","Setembro","Outubro","Novembro","Dezembro",
];

function buildMonthGrid(year: number, month: number) {
  const first = new Date(year, month, 1);
  let startDow = first.getDay();
  startDow = startDow === 0 ? 6 : startDow - 1;
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells: { day: number; current: boolean; date: Date }[] = [];
  const prevMonthDays = new Date(year, month, 0).getDate();
  for (let i = startDow - 1; i >= 0; i--) {
    cells.push({ day: prevMonthDays - i, current: false, date: new Date(year, month - 1, prevMonthDays - i) });
  }
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push({ day: d, current: true, date: new Date(year, month, d) });
  }
  while (cells.length % 7 !== 0) {
    const d = cells.length - (startDow + daysInMonth) + 1;
    cells.push({ day: d, current: false, date: new Date(year, month + 1, d) });
  }
  return cells;
}

function MonthCalendar({
  value,
  onChange,
}: {
  value: number | null;
  onChange: (v: number) => void;
}) {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth());

  const cells = buildMonthGrid(viewYear, viewMonth);

  const go = (dir: number) => {
    setViewMonth((m) => {
      const next = m + dir;
      if (next < 0) { setViewYear((y) => y - 1); return 11; }
      if (next > 11) { setViewYear((y) => y + 1); return 0; }
      return next;
    });
  };

  return (
    <div className="month-cal">
      <header className="month-cal__nav">
        <Button type="button" variant="ghost" size="sm" icon="chevron-left" onClick={() => go(-1)} aria-label="Mês anterior" />
        <span className="month-cal__title">
          {MONTH_NAMES[viewMonth]} {viewYear}
        </span>
        <Button type="button" variant="ghost" size="sm" icon="chevron-right" onClick={() => go(1)} aria-label="Próximo mês" />
      </header>
      <div className="month-cal__weekdays" role="row">
        {WEEKDAY_LABELS.map((w) => (
          <span key={w} className="month-cal__weekday">{w}</span>
        ))}
      </div>
      <div className="month-cal__grid" role="grid">
        {cells.map((c, i) => {
          const selectable = c.current && c.day <= 28;
          const selected = selectable && value === c.day;
          return (
            <button
              key={i}
              type="button"
              className={`month-cal__day${selected ? " month-cal__day--selected" : ""}${!c.current ? " month-cal__day--other" : ""}${!selectable && c.current ? " month-cal__day--disabled" : ""}`}
              disabled={!selectable}
              aria-pressed={selected || undefined}
              onClick={() => selectable && onChange(c.day)}
            >
              {c.day}
            </button>
          );
        })}
      </div>
      <p className="mini-cal__hint">
        {value !== null
          ? `Executa todo dia ${value}`
          : "Selecione um dia do mês (1–28)"}
      </p>
    </div>
  );
}

function MiniCalendar({
  periodicidade,
  value,
  onChange,
}: {
  periodicidade: PeriodicidadeBalanco;
  value: number | null;
  onChange: (v: number | null) => void;
}) {
  if (periodicidade === "SEMANAL") {
    return (
      <div className="mini-cal">
        <div className="mini-cal__week" role="group" aria-label="Dia da semana">
          {WEEK_DAYS.map((d) => (
            <button
              key={d.value}
              type="button"
              aria-pressed={value === d.value}
              onClick={() => onChange(d.value)}
            >
              {d.label}
            </button>
          ))}
        </div>
        {value !== null && (
          <span className="mini-cal__hint">
            Toda {WEEK_DAYS.find((d) => d.value === value)?.label.toLowerCase()}
          </span>
        )}
      </div>
    );
  }

  return <MonthCalendar value={value} onChange={(d) => onChange(d)} />;
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
  const [dia, setDia] = useState<number | null>(
    config?.diaExecucao ?? null
  );
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    const body = {
      periodicidade,
      diaExecucao: periodicidade === "DIARIA" ? null : dia,
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

  return (
    <Card title="Agendamento do balanço" bodyClassName="config-form">
      <div className="form-grid-2">
        <Field label="Frequência">
          <Select value={periodicidade} onChange={(e) => {
            const v = e.target.value as PeriodicidadeBalanco;
            setPeriodicidade(v);
            if (v === "DIARIA") setDia(null);
            else if (dia === null) setDia(v === "SEMANAL" ? 1 : 1);
          }}>
            <option value="DIARIA">Diária</option>
            <option value="SEMANAL">Semanal</option>
            <option value="MENSAL">Mensal</option>
          </Select>
        </Field>
      </div>
      {periodicidade !== "DIARIA" && (
        <div style={{ marginTop: 12 }}>
          <MiniCalendar
            periodicidade={periodicidade}
            value={dia}
            onChange={setDia}
          />
        </div>
      )}
      <div className="config-form__btn" style={{ marginTop: 16 }}>
        <Button
          onClick={() => void save()}
          loading={saving}
          variant="outline"
          disabled={periodicidade !== "DIARIA" && dia === null}
        >
          {config ? "Salvar agendamento" : "Criar agendamento"}
        </Button>
      </div>
    </Card>
  );
}