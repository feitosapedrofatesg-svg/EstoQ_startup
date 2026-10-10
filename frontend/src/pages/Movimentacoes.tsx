import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
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
  NumberField,
  SearchSelect,
  Segmented,
  Checkbox,
  DataTable,
  Badge,
  EmptyState,
} from "../components/UI";
import { fmtDateTime, fmtNum, fmtMoney, hojeISO, diasAtrasISO, parseDecimal } from "../lib/format";
import type {
  EstoqueDTO,
  MovimentacaoDTO,
  MovimentacaoResultadoDTO,
  ProdutoDTO,
  TipoMovimentacao,
  UnidadeMedida,
} from "../lib/types";
import { useToast } from "../store/toast";

const UNIDADES: UnidadeMedida[] = ["KG", "G", "L", "ML", "UN"];

const TIPO_LABEL: Record<TipoMovimentacao, string> = {
  ENTRADA: "Entrada",
  CONSUMO: "Consumo",
  DESPERDICIO: "Desperdício",
  AJUSTE: "Ajuste",
};

const TIPO_TONE: Record<TipoMovimentacao, "good" | "info" | "bad" | "neutral"> = {
  ENTRADA: "good",
  CONSUMO: "info",
  DESPERDICIO: "bad",
  AJUSTE: "neutral",
};

export function Movimentacoes() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [params, setParams] = useSearchParams();

  const tipo = params.get("tipo") ?? "TODOS";
  const produtoId = params.get("produto") ?? "";
  const [inicio, setInicio] = useState(diasAtrasISO(30));
  const [fim, setFim] = useState(hojeISO());
  const [showRegister, setShowRegister] = useState(false);

  const setTipo = (v: string) => {
    const next = new URLSearchParams(params);
    if (v === "TODOS") next.delete("tipo");
    else next.set("tipo", v);
    setParams(next, { replace: true });
  };

  const qs = new URLSearchParams();
  if (tipo !== "TODOS") qs.set("tipo", tipo);
  if (produtoId) qs.set("produtoId", produtoId);
  if (inicio) qs.set("inicio", `${inicio}T00:00:00`);
  if (fim) qs.set("fim", `${fim}T23:59:59`);

  const { data, loading, error, refresh } = useFetch<MovimentacaoDTO[]>(
    `/api/movimentacoes?${qs.toString()}`
  );

  const { data: produtos } = useFetch<EstoqueDTO[]>("/api/estoque");
  const produtoOpts = useMemo(
    () =>
      (produtos ?? []).map((p) => ({
        value: String(p.produtoId),
        label: p.produtoNome,
        sub: `${p.categoriaNome} · ${p.unidadeMedida}`,
      })),
    [produtos]
  );

  const totalPeriodo = useMemo(() => {
    if (!data) return { custoConsumo: 0, prejuizo: 0, compras: 0 };
    return data!.reduce(
      (acc, m) => {
        if (m.tipo === "CONSUMO") acc.custoConsumo += m.custoConsumo ?? 0;
        if (m.tipo === "DESPERDICIO") acc.prejuizo += m.valorPrejuizo ?? 0;
        if (m.tipo === "ENTRADA") acc.compras += m.valorTotalPago ?? 0;
        return acc;
      },
      { custoConsumo: 0, prejuizo: 0, compras: 0 }
    );
  }, [data]);

  return (
    <>
      <PageHeader
        title="Movimentações"
        subtitle="Tudo o que sai e entra do estoque, com histórico completo."
        actions={
          // Entrada é exclusiva do ADMIN (SecurityConfig). Consumo e desperdício
          // têm telas próprias.
          isAdmin && (
            <Button icon="plus" onClick={() => setShowRegister(true)}>
              Registrar entrada
            </Button>
          )
        }
      />

      {error && <div className="alertbanner alertbanner--bad">{error}</div>}

      <div className="mini-stats">
        <div className="mini-stat">
          <span>Compras no período</span>
          <strong>{fmtMoney(totalPeriodo.compras)}</strong>
        </div>
        <div className="mini-stat">
          <span>Consumo registrado</span>
          <strong>{fmtMoney(totalPeriodo.custoConsumo)}</strong>
        </div>
        <div className="mini-stat">
          <span>Desperdício (prejuízo)</span>
          <strong className="text-bad">{fmtMoney(totalPeriodo.prejuizo)}</strong>
        </div>
      </div>

      <Card
        title="Histórico"
        actions={
          <div className="filters">
            <Segmented
              label="Filtrar por tipo"
              value={tipo}
              onChange={setTipo}
              items={[
                { value: "TODOS", label: "Todos" },
                { value: "ENTRADA", label: "Entradas" },
                { value: "CONSUMO", label: "Consumos" },
                { value: "DESPERDICIO", label: "Desperdícios" },
                { value: "AJUSTE", label: "Ajustes" },
              ]}
            />
            <div className="filters__dates">
              <Field label="De" htmlFor="mv-inicio">
                <Input id="mv-inicio" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
              </Field>
              <Field label="Até" htmlFor="mv-fim">
                <Input id="mv-fim" type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
              </Field>
            </div>
            <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
              Atualizar
            </Button>
          </div>
        }
      >
        {loading && !data ? (
          <p className="muted">Carregando…</p>
        ) : data && data.length === 0 ? (
          <EmptyState
            title="Nenhuma movimentação no período"
            text={
              isAdmin
                ? "Use o botão Registrar entrada para lançar compras e produtos que entraram no estoque."
                : "Consulte as telas de Consumo e Desperdício para registrar as saídas do estoque."
            }
            icon="clipboard-list"
          />
        ) : (
          <DataTable
            caption="Movimentações do estoque"
            headers={["Quando", "Tipo", "Produto", "Lote", "Quantidade", "Valor", "Motivo"]}
          >
            {data?.map((m) => (
              <tr key={m.id}>
                <td className="muted-cell">{fmtDateTime(m.dataHora)}</td>
                <td>
                  <Badge tone={TIPO_TONE[m.tipo]}>{TIPO_LABEL[m.tipo]}</Badge>
                </td>
                <td>
                  <strong>{m.produtoNome}</strong>
                  {m.observacao && <span className="cell-sub">{m.observacao}</span>}
                </td>
                <td className="muted-cell">{m.loteCodigo ?? "—"}</td>
                <td>
                  <span className={m.tipo === "DESPERDICIO" ? "text-bad" : ""}>
                    {m.tipo === "ENTRADA" ? "+" : m.tipo === "AJUSTE" && (m.diferencaApurada ?? 0) >= 0 ? "+" : "−"}
                    {fmtNum(Math.abs(m.quantidade))}
                  </span>
                </td>
                <td>
                  {m.tipo === "ENTRADA"
                    ? fmtMoney(m.valorTotalPago ?? 0)
                    : m.tipo === "CONSUMO"
                      ? fmtMoney(m.custoConsumo ?? 0)
                      : m.tipo === "DESPERDICIO"
                        ? fmtMoney(m.valorPrejuizo ?? 0)
                        : fmtMoney(m.diferencaApurada ?? 0)}
                </td>
                <td className="muted-cell">
                  {m.motivo ? m.motivo.replaceAll("_", " ") : "—"}
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      {showRegister && (
        <RegisterEntrada
          initialProduto={produtoId}
          produtoOpts={produtoOpts}
          onClose={() => setShowRegister(false)}
          onDone={() => {
            setShowRegister(false);
            void refresh();
            toast.success("Entrada registrada");
          }}
        />
      )}
    </>
  );
}

/** Entrada de mercadoria: só o ADMIN lança (POST /api/entradas). */
function RegisterEntrada({
  initialProduto,
  produtoOpts,
  onClose,
  onDone,
}: {
  initialProduto: string;
  produtoOpts: { value: string; label: string; sub?: string }[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [produtoId, setProdutoId] = useState(initialProduto);
  const [quantidade, setQuantidade] = useState<number | null>(null);
  const [observacao, setObservacao] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [valorTotalPago, setValorTotalPago] = useState<number | null>(null);
  const [unidadeCompra, setUnidadeCompra] = useState<UnidadeMedida>("KG");
  const [dataValidade, setDataValidade] = useState("");
  const [semCusto, setSemCusto] = useState(false);

  const { data: detalheProduto } = useFetch<ProdutoDTO>(
    produtoId ? `/api/produtos/${produtoId}` : null
  );

  useEffect(() => {
    if (detalheProduto?.unidadeMedida) setUnidadeCompra(detalheProduto.unidadeMedida);
  }, [detalheProduto?.unidadeMedida]);

  const validate = (): string | null => {
    if (!produtoId) return "Escolha um produto.";
    if (!quantidade || quantidade <= 0) return "Informe uma quantidade maior que zero.";
    if (!semCusto && (valorTotalPago === null || valorTotalPago < 0))
      return "Informe o valor total pago, ou marque como Sem custo.";
    if (valorTotalPago === 0) setSemCusto(true);
    return null;
  };

  const submit = async () => {
    const v = validate();
    if (v) {
      setError(v);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await api.post<MovimentacaoResultadoDTO>("/api/entradas", {
        produtoId: Number(produtoId),
        quantidade,
        valorTotalPago: semCusto ? 0 : valorTotalPago ?? 0,
        unidadeCompra,
        dataValidade: dataValidade || null,
        semCusto,
        observacao: observacao || null,
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
      title="Registrar entrada"
      width="md"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button onClick={() => void submit()} loading={submitting} icon="check">
            Registrar entrada
          </Button>
        </>
      }
    >
      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          {error}
        </div>
      )}

      <SearchSelect
        label="Produto"
        options={produtoOpts}
        value={produtoId}
        onValue={(v) => {
          setProdutoId(v);
          setError(null);
        }}
        placeholder="Buscar produto…"
        required
      />

      <div className="form-grid-2">
        <NumberField
          label="Quantidade"
          value={quantidade}
          onValue={setQuantidade}
          min={0}
          placeholder="Ex.: 5"
          suffix={detalheProduto?.unidadeMedida ?? "UN"}
          error={error && quantidade && quantidade <= 0 ? error : null}
        />
        <Select
          value={unidadeCompra}
          onChange={(e) => setUnidadeCompra(e.target.value as UnidadeMedida)}
          aria-label="Unidade da compra"
        >
          {UNIDADES.map((u) => (
            <option key={u} value={u}>
              {u === "UN" ? "Unidade (UN)" : u}
            </option>
          ))}
        </Select>
      </div>
      <Field label="Valor total pago (R$)" htmlFor="ent-valor" required={!semCusto}>
        <div className="input-group">
          <input
            id="ent-valor"
            className="input"
            inputMode="decimal"
            placeholder="Ex.: 120,00"
            disabled={semCusto}
            value={valorTotalPago === null ? "" : String(valorTotalPago).replace(".", ",")}
            onChange={(e) => {
              const n = parseDecimal(e.target.value);
              setValorTotalPago(n);
            }}
          />
          <span className="input-group__suffix">R$</span>
        </div>
      </Field>
      <Checkbox checked={semCusto} onChange={setSemCusto} label="Entrada sem custo (doação, produção interna)" />
      <div className="form-grid-2">
        <Field label="Validade (opcional)" htmlFor="ent-validade">
          <Input
            id="ent-validade"
            type="date"
            value={dataValidade}
            onChange={(e) => setDataValidade(e.target.value)}
          />
        </Field>
        <Field label="Observação" htmlFor="ent-obs">
          <Input
            id="ent-obs"
            placeholder="Fornecedor, nota, lote…"
            value={observacao}
            onChange={(e) => setObservacao(e.target.value)}
          />
        </Field>
      </div>
    </Modal>
  );
}
