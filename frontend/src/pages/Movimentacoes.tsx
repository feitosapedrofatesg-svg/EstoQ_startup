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
  Textarea,
  NumberField,
  SearchSelect,
  Segmented,
  Checkbox,
  DataTable,
  Badge,
  EmptyState,
  AlertBanner,
} from "../components/UI";
import { fmtDateTime, fmtNum, fmtMoney, hojeISO, diasAtrasISO, parseDecimal } from "../lib/format";
import type {
  EstoqueDTO,
  LoteDTO,
  MovimentacaoDTO,
  MovimentacaoResultadoDTO,
  MotivoDesperdicio,
  ProdutoDTO,
  TipoMovimentacao,
  UnidadeMedida,
} from "../lib/types";
import { useToast } from "../store/toast";

const UNIDADES: UnidadeMedida[] = ["KG", "G", "L", "ML", "UN"];
const MOTIVOS: MotivoDesperdicio[] = [
  "VENCIMENTO",
  "DETERIORACAO",
  "PREPARO_INCORRETO",
  "SOBRA_NAO_APROVEITADA",
  "OUTRO",
];

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
  const { user, canMove } = useAuth();
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
          canMove && (
            <Button icon="plus" onClick={() => setShowRegister(true)}>
              Registrar movimento
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
        {loading && !data && <p className="muted">Carregando…</p>}
        {data && data.length === 0 ? (
          <EmptyState
            title="Nenhuma movimentação no período"
            text="Use o botão Registrar movimento para começar a lançar entradas, consumo e desperdício."
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
        <RegisterMovement
          userPerfil={user?.perfil}
          initialProduto={produtoId}
          produtoOpts={produtoOpts}
          onClose={() => setShowRegister(false)}
          onDone={() => {
            setShowRegister(false);
            void refresh();
            toast.success("Movimentação registrada");
          }}
        />
      )}
    </>
  );
}

type MovType = "ENTRADA" | "CONSUMO" | "DESPERDICIO";

function RegisterMovement({
  userPerfil,
  initialProduto,
  produtoOpts,
  onClose,
  onDone,
}: {
  userPerfil?: string;
  initialProduto: string;
  produtoOpts: { value: string; label: string; sub?: string }[];
  onClose: () => void;
  onDone: () => void;
}) {
  const canEntrada = userPerfil === "ADMIN";

  const [tipo, setTipo] = useState<MovType>("CONSUMO");
  const [produtoId, setProdutoId] = useState(initialProduto);
  const [quantidade, setQuantidade] = useState<number | null>(null);
  const [observacao, setObservacao] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Consumo / desperdício
  const [manualLote, setManualLote] = useState(false);
  const [loteId, setLoteId] = useState("");

  // Desperdício
  const [motivo, setMotivo] = useState<MotivoDesperdicio>("VENCIMENTO");
  const [descricaoMotivo, setDescricaoMotivo] = useState("");

  // Entrada
  const [valorTotalPago, setValorTotalPago] = useState<number | null>(null);
  const [unidadeCompra, setUnidadeCompra] = useState<UnidadeMedida>("KG");
  const [dataValidade, setDataValidade] = useState("");
  const [semCusto, setSemCusto] = useState(false);

  const lotePath = produtoId && manualLote ? `/api/lotes?produtoId=${produtoId}` : null;
  const { data: lotes } = useFetch<LoteDTO[]>(lotePath);
  const { data: detalheProduto } = useFetch<ProdutoDTO>(
    produtoId ? `/api/produtos/${produtoId}` : null
  );

  useEffect(() => {
    if (detalheProduto?.unidadeMedida) setUnidadeCompra(detalheProduto.unidadeMedida);
  }, [detalheProduto?.unidadeMedida]);

  const lotesDisponiveis = useMemo(() => {
    if (tipo === "DESPERDICIO") return lotes ?? []; // destruir busca todos
    return (lotes ?? []).filter((l) => l.disponivel && !l.vencido);
  }, [lotes, tipo]);

  const validate = (): string | null => {
    if (!produtoId) return "Escolha um produto.";
    if (!quantidade || quantidade <= 0) return "Informe uma quantidade maior que zero.";
    if (tipo === "ENTRADA") {
      if (!semCusto && (valorTotalPago === null || valorTotalPago < 0))
        return "Informe o valor total pago, ou marque como Sem custo.";
      if (valorTotalPago === 0) setSemCusto(true);
    }
    if (tipo === "DESPERDICIO" && motivo === "OUTRO" && !descricaoMotivo.trim())
      return "Explique o motivo do desperdício quando o motivo for Outro.";
    if (tipo === "CONSUMO" && manualLote && !loteId) return "Escolha o lote consumido.";
    if (tipo === "DESPERDICIO" && manualLote && !loteId)
      return "Escolha o lote descartado.";
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
    const lote = lotesDisponiveis.find((l) => String(l.id) === loteId);
    try {
      if (tipo === "ENTRADA") {
        await api.post<MovimentacaoResultadoDTO>("/api/entradas", {
          produtoId: Number(produtoId),
          quantidade,
          valorTotalPago: semCusto ? 0 : valorTotalPago ?? 0,
          unidadeCompra,
          dataValidade: dataValidade || null,
          semCusto,
          observacao: observacao || null,
        });
      } else if (tipo === "CONSUMO") {
        await api.post<MovimentacaoResultadoDTO>("/api/consumos", {
          produtoId: Number(produtoId),
          loteId: lote?.id ?? null,
          versionLote: lote?.version ?? null,
          quantidade,
          observacao: observacao || null,
        });
      } else {
        await api.post<MovimentacaoResultadoDTO>("/api/desperdicios", {
          produtoId: Number(produtoId),
          loteId: lote?.id ?? null,
          versionLote: lote?.version ?? null,
          quantidade,
          motivo,
          descricaoMotivo: motivo === "OUTRO" ? descricaoMotivo : null,
          observacao: observacao || null,
        });
      }
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
      title="Registrar movimento"
      width="md"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button onClick={() => void submit()} loading={submitting} icon="check">
            Registrar
          </Button>
        </>
      }
    >
      <Segmented
        label="Tipo de movimento"
        value={tipo}
        onChange={(t) => {
          setTipo(t as MovType);
          setError(null);
        }}
        items={
          [
            ...(canEntrada ? [{ value: "ENTRADA", label: "Entrada" }] : []),
            { value: "CONSUMO", label: "Consumo" },
            { value: "DESPERDICIO", label: "Desperdício" },
          ] as { value: string; label: string }[]
        }
      />

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
          setLoteId("");
          setError(null);
        }}
        placeholder="Buscar produto…"
        required
        error={error && !produtoId ? error : null}
      />

      {tipo === "ENTRADA" ? (
        <>
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
        </>
      ) : (
        <>
          <NumberField
            label={`Quantidade ${tipo === "CONSUMO" ? "consumida" : "descartada"}`}
            value={quantidade}
            onValue={setQuantidade}
            min={0}
            placeholder="Ex.: 2"
            suffix={detalheProduto?.unidadeMedida ?? "UN"}
            hint={tipo === "CONSUMO" ? "Sem escolher lote, o sistema consome pelo prazo de validade (FIFO)." : undefined}
            error={error && quantidade && quantidade <= 0 ? error : null}
          />

          {tipo === "CONSUMO" && (
            <Checkbox
              checked={manualLote}
              onChange={(v) => {
                setManualLote(v);
                setLoteId("");
              }}
              label="Escolho o lote manualmente"
            />
          )}

          {manualLote && lotesDisponiveis.length > 0 && (
            <SearchSelect
              label={tipo === "CONSUMO" ? "Lote consumido" : "Lote descartado"}
              options={lotesDisponiveis.map((l) => ({
                value: String(l.id),
                label: l.codigo,
                sub: `Validade ${l.dataValidade ?? "—"} · saldo ${fmtNum(l.quantidadeAtual)} ${l.unidadeMedida}`,
              }))}
              value={loteId}
              onValue={setLoteId}
              placeholder="Escolha o lote…"
            />
          )}
          {manualLote && lotesDisponiveis.length === 0 && (
            <AlertBanner tone="info">Este produto não tem lotes disponíveis para escolha.</AlertBanner>
          )}

          {tipo === "DESPERDICIO" && (
            <>
              <Field label="Motivo" htmlFor="desc-motivo" required>
                <Select
                  id="desc-motivo"
                  value={motivo}
                  onChange={(e) => setMotivo(e.target.value as MotivoDesperdicio)}
                >
                  {MOTIVOS.map((m) => (
                    <option key={m} value={m}>
                      {m.replaceAll("_", " ")}
                    </option>
                  ))}
                </Select>
              </Field>
              {motivo === "OUTRO" && (
                <Field label="Descreva o motivo" htmlFor="desc-outro" required>
                  <Textarea
                    id="desc-outro"
                    value={descricaoMotivo}
                    onChange={(e) => setDescricaoMotivo(e.target.value)}
                    placeholder="Ex.: suspeita de problema com a geladeira…"
                  />
                </Field>
              )}
            </>
          )}

          <Field label="Observação (opcional)" htmlFor="mv-obs">
            <Input
              id="mv-obs"
              placeholder="Notas internas…"
              value={observacao}
              onChange={(e) => setObservacao(e.target.value)}
            />
          </Field>

          {!manualLote && <AlertBanner tone="info">Lote não informado: o sistema aplica a regra FIFO (primeiro que vence, primeiro que sai)</AlertBanner>}
        </>
      )}
    </Modal>
  );
}