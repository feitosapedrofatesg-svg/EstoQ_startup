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
} from "../components/UI";
import { BarChart, HBars, CmvGauge } from "../components/Charts";
import {
  fmtMoney,
  fmtPct,
  fmtNum,
  fmtDateTime,
  fmtDiaSemana,
  hojeISO,
  diasAtrasISO,
  parseDecimal,
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

export function Relatorios() {
  const toast = useToast();

  return (
    <>
      <PageHeader
        title="Relatórios e CMV"
        subtitle="O custo de cada item que sai da sua cozinha, sem aproximação."
      />

      <CmvPeriodo />
      <CmvMensal />

      <div className="grid-2">
        <Desperdicio />
        <ConsumoMedio />
      </div>

      <Reposicao />
      <Pdfs toast={toast} />
      <MetaCmv />
    </>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card title={title}>
      {children}
    </Card>
  );
}

function usePeriodo() {
  const [inicio, setInicio] = useState(diasAtrasISO(30));
  const [fim, setFim] = useState(hojeISO());
  return { inicio, fim, setInicio, setFim };
}

const CMV_ROWS = (d: CmvResumoDTO) => [
  { label: "Estoque no início do período", value: fmtMoney(d.valorEstoqueInicial) },
  { label: "+ Compras no período", value: fmtMoney(d.valorCompras) },
  { label: "− Estoque no fim do período", value: fmtMoney(d.valorEstoqueFinal) },
  { label: "CMV do período", value: fmtMoney(d.cmv), strong: true },
  { label: "Receita base (informada)", value: fmtMoney(d.receitaBase ?? 0) },
];

function CmvPeriodo() {
  const { inicio, fim, setInicio, setFim } = usePeriodo();
  const [receitaBase, setReceitaBase] = useState<number | null>(null);
  const [tmpReceita, setTmpReceita] = useState("");

  const qs = new URLSearchParams();
  qs.set("inicio", inicio);
  qs.set("fim", fim);
  if (receitaBase !== null) qs.set("receitaBase", String(receitaBase));

  const { data, loading, refresh } = useFetch<CmvResumoDTO>(`/api/relatorios/cmv?${qs.toString()}`);

  return (
    <Section title="CMV no período">
      <div className="filters">
        <div className="filters__dates">
          <Field label="De" htmlFor="cmv-inicio">
            <Input id="cmv-inicio" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          </Field>
          <Field label="Até" htmlFor="cmv-fim">
            <Input id="cmv-fim" type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
          </Field>
        </div>
        <Field label="Receita base do período (R$)" htmlFor="cmv-receita">
          <div className="input-group">
            <input
              id="cmv-receita"
              className="input"
              inputMode="decimal"
              placeholder="Opcional, para calcular o % do CMV"
              value={tmpReceita}
              onChange={(e) => setTmpReceita(e.target.value)}
              onBlur={() => {
                setReceitaBase(parseDecimal(tmpReceita));
              }}
            />
            <span className="input-group__suffix">R$</span>
          </div>
        </Field>
        <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
          Recalcular
        </Button>
      </div>

      {loading && !data && <p className="muted">Calculando…</p>}
      {data && (
        <>
          <div className="cmv-hero">
            <div>
              <span className="cmv-hero__label">CMV</span>
              <strong className="cmv-hero__value">{fmtMoney(data.cmv)}</strong>
            </div>
            <div className="cmv-hero__side">
              <CmvGauge
                percentual={data.cmvPercentual}
                ideal={data.percentualIdeal}
                label="Percentual de CMV"
              />
            </div>
          </div>

          <div className="cmv-table-cols">
            <table className="table table--narrow">
              <tbody>
                {CMV_ROWS(data).map((r) => (
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
                  <td>{fmtMoney(data.valorConsumoRegistrado)}</td>
                </tr>
                <tr>
                  <td className="muted-cell">Desperdício</td>
                  <td className="text-bad">{fmtMoney(data.valorDesperdicio)}</td>
                </tr>
                <tr>
                  <td className="muted-cell">% de desperdício sobre o CMV</td>
                  <td>{fmtPct(data.percentualDesperdicioSobreCmv)}</td>
                </tr>
                <tr>
                  <td className="muted-cell">Perdas não explicadas</td>
                  <td className="text-bad">{fmtMoney(data.valorPerdasNaoExplicadas)}</td>
                </tr>
                <tr>
                  <td className="muted-cell">Diferença para a meta</td>
                  <td>{fmtPct(data.diferencaPercentualParaMeta)}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </>
      )}
    </Section>
  );
}

function CmvMensal() {
  const { data } = useFetch<CmvMensalDTO[]>("/api/relatorios/cmv/mensal");
  const bars = useMemo(
    () =>
      (data ?? []).map((d) => ({
        label: d.periodo.slice(3, 5) + "/" + d.periodo.slice(0, 4),
        value: d.cmv,
        hint: `Compras ${fmtMoney(d.valorCompras)}`,
      })),
    [data]
  );

  return (
    <Section title="CMV mês a mês">
      {data && data.length === 0 ? (
        <EmptyState title="Sem histórico mensal" text="O histórico aparece conforme os meses são fechados." />
      ) : (
        <>
          <BarChart data={bars} ariaLabel="CMV por mês" />
          <DataTable caption="CMV mensal" headers={["Período", "CMV", "Compras", "Desperdício"]}>
            {(data ?? []).map((d) => (
              <tr key={d.periodo}>
                <td>
                  <strong>{d.periodo.slice(3, 5)}/{d.periodo.slice(0, 4)}</strong>
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

function Desperdicio() {
  const { inicio, fim, setInicio, setFim } = usePeriodo();
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
    const map = new Map<string, { quantidade: number; prejuizo: number }>();
    for (const g of agreg ?? []) {
      const cur = map.get(g.motivo) ?? { quantidade: 0, prejuizo: 0 };
      cur.quantidade += g.quantidade;
      cur.prejuizo += g.valorPrejuizo;
      map.set(g.motivo, cur);
    }
    return [...map.entries()].map(([motivo, v]) => ({
      label: motivo.replaceAll("_", " "),
      value: Math.round(v.prejuizo * 100) / 100,
      sub: `${fmtNum(v.quantidade)} unidades perdidas`,
    }));
  }, [agreg]);

  return (
    <Section title="Desperdício">
      <div className="filters">
        <div className="filters__dates">
          <Field label="De" htmlFor="des-inicio">
            <Input id="des-inicio" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          </Field>
          <Field label="Até" htmlFor="des-fim">
            <Input id="des-fim" type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
          </Field>
        </div>
        <p className="filters__total">
          Prejuízo: <strong className="text-bad">{fmtMoney(totalPrejuizo)}</strong>
        </p>
      </div>

      <h3 className="section-title">Por motivo</h3>
      {porMotivo.length > 0 ? (
        <HBars rows={porMotivo} ariaLabel="Desperdício por motivo" />
      ) : (
        <p className="muted">Sem desperdício no período.</p>
      )}

      {lista && lista.length > 0 && (
        <>
          <h3 className="section-title">Lançamentos</h3>
          <DataTable caption="Lançamentos de desperdício" headers={["Quando", "Produto", "Lote", "Motivo", "Quantidade", "Prejuízo"]}>
            {lista.map((d) => (
              <tr key={`${d.dataHora}-${d.produtoId}-${d.loteCodigo ?? ""}`}>
                <td className="muted-cell">{fmtDateTime(d.dataHora)}</td>
                <td>
                  <strong>{d.produtoNome}</strong>
                </td>
                <td className="muted-cell">{d.loteCodigo ?? "—"}</td>
                <td>{d.motivo.replaceAll("_", " ")}</td>
                <td>{fmtNum(d.quantidade)}</td>
                <td className="text-bad">{fmtMoney(d.valorPrejuizo)}</td>
              </tr>
            ))}
          </DataTable>
        </>
      )}
    </Section>
  );
}

function ConsumoMedio() {
  const [dias, setDias] = useState(30);
  const { data: consumo } = useFetch<ConsumoMedioDTO[]>(
    `/api/relatorios/consumo-medio?dias=${dias}`
  );
  const { data: diaSemana } = useFetch<ConsumoDiaSemanaDTO[]>("/api/relatorios/consumo/dia-semana");

  const bars = useMemo(
    () =>
      (diaSemana ?? []).map((d) => ({
        label: fmtDiaSemana(d.diaSemana).slice(0, 3),
        value: d.quantidadeMedia,
        hint: `Total ${fmtNum(d.quantidadeTotal)}`,
      })),
    [diaSemana]
  );
  const maxIdx = bars.reduce((a, b, i) => (b.value > bars[a].value ? i : a), 0);

  return (
    <Section title="Consumo médio">
      <div className="filters">
        <Field label="Janela (dias)" htmlFor="cons-dias">
          <Input id="cons-dias" type="number" min={1} max={365} value={dias} onChange={(e) => setDias(parseInt(e.target.value || "30", 10))} />
        </Field>
        <p className="muted">Média diária por produto nos últimos {dias} dias.</p>
      </div>
      {consumo && consumo.length > 0 && (
        <>
          <h3 className="section-title">Por dia da semana</h3>
          <BarChart
            data={bars}
            accent={[maxIdx]}
            unit="média"
            ariaLabel="Consumo médio por dia da semana"
          />
          <DataTable caption="Consumo médio diário por produto" headers={["Produto", "Total no período", "Média/dia"]}>
            {consumo.slice(0, 20).map((c) => (
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
        </>
      )}
    </Section>
  );
}

function Reposicao() {
  const { data } = useFetch<ReposicaoSugeridaDTO[]>("/api/relatorios/reposicao-sugerida?dias=7");
  const total = useMemo(() => (data ?? []).length, [data]);

  return (
    <Section title={`Reposição sugerida${total ? ` (${total})` : ""}`}>
      {data && data.length === 0 ? (
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
    </Section>
  );
}

function Pdfs({ toast }: { toast: ReturnType<typeof useToast> }) {
  const [busy, setBusy] = useState<string | null>(null);
  const docs: { tipo: string; label: string }[] = [
    { tipo: "estoque-atual", label: "Estoque atual" },
    { tipo: "proximos-vencimento", label: "Próximos vencimentos" },
    { tipo: "vencidos", label: "Vencidos" },
    { tipo: "consumo-medio", label: "Consumo médio" },
    { tipo: "reposicao-sugerida", label: "Reposição sugerida" },
    { tipo: "cmv", label: "CMV" },
    { tipo: "desperdicio", label: "Desperdício" },
  ];

  const download = async (tipo: string, label: string) => {
    setBusy(tipo);
    try {
      const blob = await api.blob(`/api/relatorios/pdf/${tipo}`);
      const estampa = new Date().toISOString().slice(0, 10);
      downloadBlob(blob, `estoQ-${tipo}-${estampa}.pdf`);
      toast.success(`${label} gerado em PDF`);
    } catch (e) {
      toast.error("Não foi possível gerar o PDF", (e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Section title="Exportar em PDF">
      <div className="pdf-grid">
        {docs.map((d) => (
          <Button
            key={d.tipo}
            variant="outline"
            icon="download"
            loading={busy === d.tipo}
            onClick={() => void download(d.tipo, d.label)}
          >
            {d.label}
          </Button>
        ))}
      </div>
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