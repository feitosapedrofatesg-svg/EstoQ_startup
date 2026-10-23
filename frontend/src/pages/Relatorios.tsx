import { useMemo, useState, type ReactNode } from "react";
import { useFetch } from "../lib/hooks";
import { api, downloadBlob } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  Field,
  Input,
  NumberField,
  DataTable,
  EmptyState,
  Modal,
  Select,
} from "../components/UI";
import { BarChart, HBars, CmvGauge } from "../components/Charts";
import { AvisoFaturamento, FaturamentoDiario } from "../components/FaturamentoDiario";
import { useSearchParams } from "react-router-dom";
import {
  fmtMoney,
  fmtPct,
  fmtNum,
  fmtDateTime,
  fmtDiaSemana,
  hojeISO,
} from "../lib/format";
import type {
  CmvMensalDTO,
  CmvResumoDTO,
  ConsumoDiaSemanaDTO,
  ConsumoMedioDTO,
  DesperdicioAgregadoDTO,
  DesperdicioDTO,
  ParametroCmvDTO,
  ReposicaoSugeridaDTO,
} from "../lib/types";
import { useToast } from "../store/toast";
import { ListaComRolagem } from "../responsividade/tablet/ListaComRolagem";

export function Relatorios() {
  const toast = useToast();
  const [parametros] = useSearchParams();
  const de = parametros.get("inicio");
  const ate = parametros.get("fim");
  const dataValida = (d: string | null) => !!d && /^\d{4}-\d{2}-\d{2}$/.test(d)
    && !Number.isNaN(Date.parse(d)) && new Date(d).toISOString().slice(0, 10) === d;
  const periodoValido = dataValida(de) && dataValida(ate) && de! <= ate!;
  const [inicio, setInicio] = useState(() => periodoValido ? de! : hojeISO());
  const [fim, setFim] = useState(() => periodoValido ? ate! : hojeISO());
  const periodo = { inicio, fim };

  return (
    <>
      <PageHeader
        title="Relatórios e CMV"
        subtitle="O custo de cada item que sai da sua cozinha, sem aproximação."
      />

      <CmvPeriodo key={`${inicio}|${fim}`} {...periodo} setInicio={setInicio} setFim={setFim} />
      <CmvMensal {...periodo} />

      <div className="grid-2">
        <Desperdicio {...periodo} />
        <ConsumoMedio {...periodo} />
      </div>

      <Reposicao />
      <ExportacaoRelatorios toast={toast} {...periodo} />
      <MetaCmv />
    </>
  );
}

function Section({ title, children, collapsible = false }: { title: string; children: ReactNode; collapsible?: boolean }) {
  return (
    <Card title={title} collapsible={collapsible}>
      {children}
    </Card>
  );
}

const CMV_ROWS = (d: CmvResumoDTO) => [
  { label: "Estoque no início do período", value: fmtMoney(d.valorEstoqueInicial) },
  { label: "+ Compras no período", value: fmtMoney(d.valorCompras) },
  { label: "− Estoque no fim do período", value: fmtMoney(d.valorEstoqueFinal) },
  { label: "CMV do período", value: fmtMoney(d.cmv), strong: true },
  { label: "Faturamento usado no cálculo", value: d.receitaBase == null ? "Pendente" : fmtMoney(d.receitaBase) },
];

interface PeriodoRelatorios { inicio: string; fim: string }

/** Todos os blocos compartilham o mesmo intervalo, incluindo as exportações. */
function diasPeriodo({ inicio, fim }: PeriodoRelatorios) {
  return Math.max(1, Math.round((Date.parse(fim) - Date.parse(inicio)) / 86400000) + 1);
}

function CmvPeriodo({ inicio, fim, setInicio, setFim }: PeriodoRelatorios & { setInicio: (data: string) => void; setFim: (data: string) => void }) {
  const qs = new URLSearchParams({ inicio, fim });
  const { data: dataAtual, loading: calculando, error, refresh } = useFetch<CmvResumoDTO>(`/api/relatorios/cmv?${qs}`);

  return (
    <Section title="Período dos relatórios e CMV">
      <div className="cmv-filters">
        <div className="cmv-filters__dates">
          <Field className="cmv-filter-field" label="De" htmlFor="relatorios-inicio">
            <Input id="relatorios-inicio" type="date" max={fim} value={inicio} onChange={e => { if (e.target.value && e.target.value <= fim) setInicio(e.target.value); }} />
          </Field>
          <Field className="cmv-filter-field" label="Até" htmlFor="relatorios-fim">
            <Input id="relatorios-fim" type="date" min={inicio} value={fim} onChange={e => { if (e.target.value && e.target.value >= inicio) setFim(e.target.value); }} />
          </Field>
        </div>
        <Button
          className="cmv-filters__refresh"
          variant="accent"
          icon="refresh"
          loading={calculando}
          onClick={() => void refresh()}
        >
          {calculando ? "Calculando" : "Recalcular"}
        </Button>
      </div>

      {error && <p className="alertbanner alertbanner--bad" role="alert">{error}</p>}
      {calculando && (
        <p className="muted">Calculando…</p>
      )}
      {dataAtual && (
        <>
          <div className="cmv-hero">
            <div>
              <span className="cmv-hero__label">CMV</span>
              <strong className="cmv-hero__value">{fmtMoney(dataAtual.cmv)}</strong>
            </div>
            <div className="cmv-hero__side">
              <CmvGauge
                percentual={dataAtual.cmvPercentual}
                ideal={dataAtual.percentualIdeal}
                label="Percentual de CMV"
              />
            </div>
          </div>

          <AvisoFaturamento {...dataAtual} />
          <div className="cmv-table-cols">
            <table className="table table--narrow">
              <tbody>
                {CMV_ROWS(dataAtual).map((r) => (
                  <tr key={r.label}>
                    <td className="muted-cell">{r.label}</td>
                    <td className={r.strong ? "cell-total" : ""}>{r.value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <table className="table table--narrow">
              <tbody>
                <tr>
                  <td className="muted-cell">Consumo registrado</td>
                  <td>{fmtMoney(dataAtual.valorConsumoRegistrado)}</td>
                </tr>
                <tr>
                  <td className="muted-cell">Desperdício</td>
                  <td className="text-bad">{fmtMoney(dataAtual.valorDesperdicio)}</td>
                </tr>
                <tr>
                  <td className="muted-cell">% de desperdício sobre o CMV</td>
                  <td>{fmtPct(dataAtual.percentualDesperdicioSobreCmv)}</td>
                </tr>
                <tr>
                  <td className="muted-cell">Perdas não explicadas</td>
                  <td className="text-bad">{fmtMoney(dataAtual.valorPerdasNaoExplicadas)}</td>
                </tr>
                <tr>
                  <td className="muted-cell">Diferença para a meta</td>
                  <td>{fmtPct(dataAtual.diferencaPercentualParaMeta)}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </>
      )}
      <FaturamentoDiario key={`${inicio}|${fim}`} inicio={inicio} fim={fim} historico />
    </Section>
  );
}

function CmvMensal({ inicio, fim }: PeriodoRelatorios) {
  const { data, loading } = useFetch<CmvMensalDTO[]>(`/api/relatorios/cmv/mensal?inicio=${inicio}&fim=${fim}`);
  const bars = useMemo(
    () =>
      (data ?? []).map((d) => ({
        label: d.periodo.slice(5, 7) + "/" + d.periodo.slice(0, 4),
        value: d.cmv,
        hint: `Compras ${fmtMoney(d.valorCompras)}`,
      })),
    [data]
  );

  return (
    <Section title="CMV mês a mês no período">
      {loading ? (
        <p className="muted">Carregando…</p>
      ) : data && data.length === 0 ? (
        <EmptyState title="Sem histórico mensal" text="O histórico aparece conforme os meses são fechados." />
      ) : (
        <>
          <BarChart data={bars} ariaLabel="CMV por mês" />
          <DataTable caption="CMV mensal" headers={["Período", "CMV", "Compras", "Desperdício"]}>
            {(data ?? []).map((d) => (
              <tr key={d.periodo}>
                <td>
                  <strong>{d.periodo.slice(5, 7)}/{d.periodo.slice(0, 4)}</strong>
                </td>
                <td>{fmtMoney(d.cmv)}</td>
                <td>{fmtMoney(d.valorCompras)}</td>
                <td className="text-bad">{fmtMoney(d.valorDesperdicio)}</td>
              </tr>
            ))}
          </DataTable>
        </>
      )}
    </Section>
  );
}

function Desperdicio({ inicio, fim }: PeriodoRelatorios) {
  const qs = new URLSearchParams();
  qs.set("inicio", inicio);
  qs.set("fim", fim);

  const { data: lista } = useFetch<DesperdicioDTO[]>(`/api/relatorios/desperdicio?${qs.toString()}`);
  const { data: agreg } = useFetch<DesperdicioAgregadoDTO[]>(
    `/api/relatorios/desperdicio/agregado?${qs.toString()}`
  );

  const totalPrejuizo = useMemo(
    () => (lista ?? []).reduce((a, m) => a + (m.valorPrejuizo ?? 0), 0),
    [lista]
  );

  const porMotivo = useMemo(() => {
    const map = new Map<string, { prejuizo: number }>();
    for (const g of agreg ?? []) {
      const cur = map.get(g.motivo) ?? { prejuizo: 0 };
      cur.prejuizo += g.valorPrejuizo;
      map.set(g.motivo, cur);
    }
    return [...map.entries()].map(([motivo, v]) => ({
      label: motivo.replaceAll("_", " "),
      value: Math.round(v.prejuizo * 100) / 100,
      sub: "Prejuízo no período",
    }));
  }, [agreg]);

  return (
    <Section title="Desperdício">
      <div className="filters">
        <p className="filters__total">
          Prejuízo: <strong className="text-bad">{fmtMoney(totalPrejuizo)}</strong>
        </p>
      </div>

      <h3 className="section-title">Por motivo</h3>
      {porMotivo.length > 0 ? (
        <HBars rows={porMotivo} ariaLabel="Desperdício por motivo" tone="bad" />
      ) : (
        <p className="muted">Sem desperdício no período.</p>
      )}

      {lista && lista.length > 0 && (
        <>
          <h3 className="section-title">Lançamentos</h3>
          <ListaComRolagem quantidade={lista.length}>
            <DataTable caption="Lançamentos de desperdício" headers={["Quando", "Produto", "Lote", "Motivo", "Quantidade", "Prejuízo"]}>
              {lista.map((d) => (
                <tr key={`${d.dataHora}-${d.produtoId}-${d.loteCodigo ?? ""}`}>
                  <td className="muted-cell">{fmtDateTime(d.dataHora)}</td>
                  <td>
                    <strong>{d.produtoNome}</strong>
                  </td>
                  <td className="muted-cell">{d.loteCodigo ?? "—"}</td>
                  <td>{d.motivo.replaceAll("_", " ")}</td>
                  <td>{fmtNum(d.quantidade)} {d.unidadeMedida?.toLowerCase()}</td>
                  <td className="text-bad">{fmtMoney(d.valorPrejuizo)}</td>
                </tr>
              ))}
            </DataTable>
          </ListaComRolagem>
        </>
      )}
    </Section>
  );
}

function ConsumoMedio({ inicio, fim }: PeriodoRelatorios) {
  const dias = diasPeriodo({ inicio, fim });
  const { data: consumo } = useFetch<ConsumoMedioDTO[]>(
    `/api/relatorios/consumo-medio?dias=${dias}&fim=${fim}`
  );
  const { data: diaSemana } = useFetch<ConsumoDiaSemanaDTO[]>(`/api/relatorios/consumo/dia-semana?inicio=${inicio}&fim=${fim}`);

  const bars = useMemo(
    () =>
      (diaSemana ?? []).map((d) => ({
        label: fmtDiaSemana(d.diaSemana).slice(0, 3),
        value: d.valorMedio,
        hint: `Total ${fmtMoney(d.valorTotal)}`,
      })),
    [diaSemana]
  );
  const maxIdx = bars.reduce((a, b, i) => (b.value > bars[a].value ? i : a), 0);

  return (
    <Section title="Consumo médio">
      <p className="muted">Média diária por produto nos {dias} dias do período selecionado.</p>
      {consumo && consumo.length > 0 && (
        <>
          <h3 className="section-title">Custo médio por dia da semana</h3>
          <BarChart
            data={bars}
            accent={[maxIdx]}
            unit="R$"
            ariaLabel="Custo médio de consumo por dia da semana"
          />
          <ListaComRolagem quantidade={consumo.length}>
            <DataTable caption="Consumo médio diário por produto" headers={["Produto", "Total no período", "Média/dia"]}>
              {consumo.map((c) => (
                <tr key={c.produtoId}>
                  <td>
                    <strong>{c.produtoNome}</strong>
                  </td>
                  <td>
                    {fmtNum(c.totalConsumidoPeriodo)} {c.unidadeMedida.toLowerCase()}
                  </td>
                  <td>
                    {fmtNum(c.consumoMedioDiario)} {c.unidadeMedida.toLowerCase()}
                  </td>
                </tr>
              ))}
            </DataTable>
          </ListaComRolagem>
        </>
      )}
    </Section>
  );
}

function Reposicao() {
  const { data, loading } = useFetch<ReposicaoSugeridaDTO[]>("/api/relatorios/reposicao-sugerida?dias=7");
  const total = useMemo(() => (data ?? []).length, [data]);

  return (
    <Card
      title={`Reposição sugerida${total ? ` (${total})` : ""}`}
      collapsible
    >
      {loading ? (
          <p className="muted">Carregando…</p>
        ) : data && data.length === 0 ? (
          <EmptyState
            title="Nada a repor"
            text="Todos os produtos estão nos níveis ideais para os próximos dias."
            icon="check-circle"
          />
        ) : (
          <DataTable caption="Reposição sugerida" headers={["Produto", "Categoria", "Saldo", "Mínimo", "Consumo/dia", "Comprar"]}>
            {data?.map((r) => (
              <tr key={r.produtoId}>
                <td>
                  <strong>{r.produtoNome}</strong>
                </td>
                <td className="muted-cell">{r.categoriaNome}</td>
                <td>
                  {fmtNum(r.saldoAtual)} {r.unidadeMedida}
                </td>
                <td className="muted-cell">
                  {fmtNum(r.estoqueMinimo)} {r.unidadeMedida}
                </td>
                <td className="muted-cell">
                  {fmtNum(r.consumoMedioDiario)} {r.unidadeMedida}/dia
                </td>
                <td>
                  <strong className="text-good">
                    +{fmtNum(r.quantidadeSugerida)} {r.unidadeMedida}
                  </strong>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
    </Card>
  );
}

const RELATORIOS_EXPORTACAO = [
  { tipo: "estoque-atual", label: "Estoque atual", grupo: "Estoque", usaPeriodo: false },
  { tipo: "proximos-vencimento", label: "Próximos vencimentos", grupo: "Estoque", usaPeriodo: false },
  { tipo: "vencidos", label: "Vencidos", grupo: "Estoque", usaPeriodo: false },
  { tipo: "produtos-abertos", label: "Embalagens abertas", grupo: "Estoque", usaPeriodo: false },
  { tipo: "reposicao-sugerida", label: "Reposição sugerida", grupo: "Estoque", usaPeriodo: false },
  { tipo: "consumo-medio", label: "Consumo médio", grupo: "Consumo e perdas", usaPeriodo: true },
  { tipo: "consumo-dia-semana", label: "Consumo por dia da semana", grupo: "Consumo e perdas", usaPeriodo: true },
  { tipo: "desperdicio", label: "Desperdício", grupo: "Consumo e perdas", usaPeriodo: true },
  { tipo: "faturamento", label: "Faturamento", grupo: "Financeiro", usaPeriodo: true },
  { tipo: "cmv", label: "CMV", grupo: "Financeiro", usaPeriodo: true },
  { tipo: "cmv-mensal", label: "CMV mensal", grupo: "Financeiro", usaPeriodo: true },
];

function ExportacaoRelatorios({ toast, inicio, fim }: PeriodoRelatorios & { toast: ReturnType<typeof useToast> }) {
  const [aberto, setAberto] = useState(false);
  const [tipo, setTipo] = useState(RELATORIOS_EXPORTACAO[0].tipo);
  const [formato, setFormato] = useState<"pdf" | "csv">("pdf");
  const [busy, setBusy] = useState(false);
  const rel = RELATORIOS_EXPORTACAO.find(r => r.tipo === tipo) ?? RELATORIOS_EXPORTACAO[0];

  const exportar = async () => {
    setBusy(true);
    try {
      const qs = new URLSearchParams({ tipo, formato, inicio, fim, dias: String(diasPeriodo({ inicio, fim })) });
      const blob = await api.blob(`/api/relatorios/exportar?${qs}`);
      downloadBlob(blob, `estoQ-${tipo}-${inicio}-a-${fim}.${formato}`);
      toast.success(rel.usaPeriodo ? "Relatório gerado com o período selecionado" : "Relatório gerado com a posição atual");
      setAberto(false);
    } catch (e) {
      toast.error("Não foi possível exportar o relatório", (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Section title="Exportar relatórios">
      <p className="muted">Um único ponto para PDF ou CSV. Relatórios de estoque, vencimentos, embalagens abertas e reposição usam a posição atual; consumo, desperdício, faturamento e CMV usam o período definido acima.</p>
      <Button variant="accent" icon="download" onClick={() => setAberto(true)}>Exportar relatório</Button>
      <Modal open={aberto} onClose={() => setAberto(false)} title="Exportar relatório" footer={<>
        <Button variant="ghost" onClick={() => setAberto(false)}>Cancelar</Button>
        <Button variant="accent" icon="download" loading={busy} onClick={() => void exportar()}>Exportar {formato.toUpperCase()}</Button>
      </>}>
        <div className="form-grid">
          <Field label="Relatório" htmlFor="export-tipo">
            <Select id="export-tipo" value={tipo} onChange={e => setTipo(e.target.value)}>
              {RELATORIOS_EXPORTACAO.map(r => <option key={r.tipo} value={r.tipo}>{r.label} — {r.grupo}{r.usaPeriodo ? " (período)" : " (posição atual)"}</option>)}
            </Select>
          </Field>
          <Field label="Formato" htmlFor="export-formato">
            <Select id="export-formato" value={formato} onChange={e => setFormato(e.target.value as "pdf" | "csv")}>
              <option value="pdf">PDF</option>
              <option value="csv">CSV</option>
            </Select>
          </Field>
          <Field label="Período" htmlFor="export-periodo">
            <Input id="export-periodo" value={rel.usaPeriodo ? `${inicio} a ${fim}` : "Posição atual"} readOnly />
          </Field>
        </div>
        <p className="muted">O CSV usa separador ponto e vírgula, com BOM para abrir direto no Excel em pt-BR.</p>
      </Modal>
    </Section>
  );
}

function MetaCmv() {
  const { data, refresh } = useFetch<ParametroCmvDTO>("/api/parametros-cmv");
  const [value, setValue] = useState<number | null>(null);
  const toast = useToast();

  const save = async () => {
    if (value === null || !data) return;
    try {
      await api.put("/api/parametros-cmv", {
        id: data.id,
        version: data.version,
        percentualIdeal: value,
      });
      toast.success("Meta de CMV atualizada");
      void refresh();
    } catch (e) {
      toast.error("Não foi possível salvar", (e as Error).message);
    }
  };

  return (
    <Section title="Meta de CMV">
      <div className="meta-cmv">
        <NumberField
          label="Percentual ideal de CMV (%)"
          value={value ?? (data ? data.percentualIdeal : null)}
          onValue={setValue}
          min={0}
          max={100}
          placeholder="Ex.: 35"
          hint="Compare o desempenho das cozinhas com essa meta em relatórios e no painel."
        />
        <Button onClick={() => void save()} icon="check">
          Salvar meta
        </Button>
      </div>
    </Section>
  );
}
