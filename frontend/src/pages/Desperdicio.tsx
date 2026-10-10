import { useId, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useFetch } from "../lib/hooks";
import { api } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  NumberField,
  SearchSelect,
  Field,
  Input,
  Select,
  Textarea,
  Checkbox,
  AlertBanner,
  DataTable,
  EmptyState,
  Modal,
  StatusBadge,
} from "../components/UI";
import { fmtNum, fmtDate, fmtDateTime, fmtMoney, diasAtrasISO } from "../lib/format";
import type {
  EstoqueDTO,
  LoteDTO,
  MovimentacaoDTO,
  MovimentacaoResultadoDTO,
  MotivoDesperdicio,
  ProdutoAbertoDTO,
  ProdutoDTO,
} from "../lib/types";
import { useToast } from "../store/toast";
import { AcaoProdutoAbertoModal } from "../components/ProdutoAbertoModals";

const MOTIVOS: MotivoDesperdicio[] = [
  "VENCIMENTO",
  "DETERIORACAO",
  "PREPARO_INCORRETO",
  "SOBRA_NAO_APROVEITADA",
  "OUTRO",
];

const MOTIVO_LABEL: Record<MotivoDesperdicio, string> = {
  VENCIMENTO: "Vencimento",
  DETERIORACAO: "Deterioração",
  PREPARO_INCORRETO: "Preparo incorreto",
  SOBRA_NAO_APROVEITADA: "Sobra não aproveitada",
  OUTRO: "Outro",
};

/** Lotes para descarte e histórico de perdas, incluindo déficits de balanço. */
export function Desperdicio() {
  const toast = useToast();
  const [params] = useSearchParams();
  const produtoParam = params.get("produto") ?? "";
  const [alvo, setAlvo] = useState<ProdutoAbertoDTO | null>(null);
  const [loteDescarte, setLoteDescarte] = useState<LoteDTO | null>(null);
  const [revisao, setRevisao] = useState(0);
  const [situacao, setSituacao] = useState("VENCIDOS");
  const [buscaLotes, setBuscaLotes] = useState("");
  const [buscaHistorico, setBuscaHistorico] = useState("");
  const [motivoFiltro, setMotivoFiltro] = useState("TODOS");
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");

  const { data: lotes, loading: loadingLotes, error: erroLotes, refresh: refreshLotes } =
    useFetch<LoteDTO[]>("/api/lotes", [], true);
  const { data: abertos, loading: loadingAbertos, error: erroAbertos, refresh: refreshAbertos } =
    useFetch<ProdutoAbertoDTO[]>("/api/produtos-abertos?finalizado=false", [], true);

  const periodoInvalido = !!inicio && !!fim && inicio > fim;
  // A API usa fim exclusivo; incluir todo o último dia selecionado.
  const fimExclusivo = fim
    ? (() => {
        const d = new Date(`${fim}T12:00:00`);
        d.setDate(d.getDate() + 1);
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      })()
    : diasAtrasISO(-1);
  const { data: registros, loading: loadingRegistros, error: erroRegistros, refresh: refreshRegistros } =
    useFetch<MovimentacaoDTO[]>(periodoInvalido ? null :
      `/api/movimentacoes?tipo=DESPERDICIO&inicio=${inicio || "1970-01-01"}T00:00:00&fim=${fimExclusivo}T00:00:00`, [], true);

  const lotesVisiveis = useMemo(() => (lotes ?? []).filter((l) =>
    l.quantidadeAtual > 0 && (situacao !== "VENCIDOS" || l.vencido) &&
    `${l.produtoNome} ${l.codigo}`.toLocaleLowerCase("pt-BR").includes(buscaLotes.toLocaleLowerCase("pt-BR"))
  ), [lotes, situacao, buscaLotes]);
  const historico = useMemo(() => (registros ?? []).filter((m) => {
    const balanco = m.tipo === "AJUSTE";
    const motivo = balanco ? "BALANCO" : m.motivo;
    return (motivoFiltro === "TODOS" || motivoFiltro === motivo) &&
      `${m.produtoNome} ${m.loteCodigo ?? ""} ${m.descricaoMotivo ?? ""} ${m.observacao ?? ""}`
        .toLocaleLowerCase("pt-BR").includes(buscaHistorico.toLocaleLowerCase("pt-BR"));
  }).sort((a, b) => b.dataHora.localeCompare(a.dataHora) || b.id - a.id),
  [registros, motivoFiltro, buscaHistorico]);
  const prejuizo = historico.reduce((total, m) => total + (m.valorPrejuizo ?? 0), 0);

  const recarregar = () => {
    void refreshLotes();
    void refreshAbertos();
    void refreshRegistros();
    setRevisao((r) => r + 1);
  };

  return (
    <>
      <PageHeader title="Desperdício" subtitle="Vencidos para descarte e todas as perdas da cozinha, incluindo diferenças negativas do balanço." />

      <Card title="Itens para excluir do estoque" actions={
        <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refreshLotes()}>Atualizar</Button>
      }>
        <div className="form-grid-2">
          <Field label="Situação" htmlFor="desp-situacao">
            <Select id="desp-situacao" value={situacao} onChange={(e) => setSituacao(e.target.value)}>
              <option value="VENCIDOS">Lotes vencidos</option>
              <option value="TODOS">Todos os lotes com saldo</option>
            </Select>
          </Field>
          <Field label="Buscar produto ou lote" htmlFor="desp-busca-lotes">
            <Input id="desp-busca-lotes" value={buscaLotes} onChange={(e) => setBuscaLotes(e.target.value)} placeholder="Nome do produto ou código…" />
          </Field>
        </div>
        <p className="muted">Para descartar um produto deteriorado, escolha “Todos os lotes com saldo” e informe o motivo na exclusão.</p>
        {erroLotes && <AlertBanner tone="bad">{erroLotes}</AlertBanner>}
        {loadingLotes && !lotes ? <p className="muted">Carregando lotes…</p> : lotesVisiveis.length === 0 ? (
          <EmptyState title={situacao === "VENCIDOS" ? "Nenhum lote vencido com saldo" : "Nenhum lote encontrado"}
            text="Os itens totalmente descartados ficam no histórico abaixo." icon="trash" />
        ) : (
          <DataTable caption="Lotes com saldo para descarte" headers={["Produto", "Lote", "Validade", "Quantidade", "Valor", "Situação", "Ação"]}>
            {lotesVisiveis.map((l) => (
              <tr key={l.id}>
                <td><strong>{l.produtoNome}</strong></td>
                <td>{l.codigo}</td>
                <td className={l.vencido ? "text-bad" : ""}>{fmtDate(l.dataValidade)}</td>
                <td>{fmtNum(l.quantidadeAtual)} {l.unidadeMedida.toLowerCase()}</td>
                <td>{fmtMoney(l.quantidadeAtual * l.precoUnitario)}</td>
                <td><StatusBadge label={l.vencido ? "Vencido" : "No estoque"} tone={l.vencido ? "bad" : "neutral"} /></td>
                <td><Button size="sm" variant="danger" onClick={() => setLoteDescarte(l)}>Excluir do estoque</Button></td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      <Card title="Registrar outra perda">
        <FormularioDesperdicio key={`${produtoParam}-${revisao}`} produtoInicial={produtoParam} onDone={() => {
          recarregar();
          toast.success("Desperdício registrado e estoque atualizado");
        }} />
      </Card>

      <Card title="Histórico geral de desperdício" actions={
        <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refreshRegistros()}>Atualizar</Button>
      }>
        <div className="form-grid-2">
          <Field label="Buscar produto ou lote" htmlFor="desp-busca-historico">
            <Input id="desp-busca-historico" value={buscaHistorico} onChange={(e) => setBuscaHistorico(e.target.value)} placeholder="Buscar no histórico…" />
          </Field>
          <Field label="Motivo da perda" htmlFor="desp-filtro-motivo">
            <Select id="desp-filtro-motivo" value={motivoFiltro} onChange={(e) => setMotivoFiltro(e.target.value)}>
              <option value="TODOS">Todos os motivos</option>
              {MOTIVOS.map((m) => <option key={m} value={m}>{MOTIVO_LABEL[m]}</option>)}
              <option value="BALANCO">Diferença negativa de balanço</option>
            </Select>
          </Field>
          <Field label="De (opcional)" htmlFor="desp-inicio">
            <Input id="desp-inicio" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          </Field>
          <Field label="Até (opcional)" htmlFor="desp-fim">
            <Input id="desp-fim" type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
          </Field>
        </div>
        <p className="muted">{historico.length} registro(s) · Prejuízo: <strong>{fmtMoney(prejuizo)}</strong></p>
        <p className="muted">As perdas deste histórico já foram baixadas do estoque. Diferenças negativas entram quando os ajustes do balanço são aplicados.</p>
        {periodoInvalido && <AlertBanner tone="bad">A data inicial deve ser anterior ou igual à final.</AlertBanner>}
        {erroRegistros && <AlertBanner tone="bad">{erroRegistros}</AlertBanner>}
        {!periodoInvalido && (loadingRegistros && !registros ? <p className="muted">Carregando histórico…</p> : historico.length === 0 ? (
          <EmptyState title="Nenhuma perda encontrada" text="Registre um descarte ou ajuste os filtros do histórico." icon="trash" />
        ) : (
          <DataTable caption="Todos os desperdícios registrados" headers={["Quando", "Produto", "Lote", "Quantidade", "Motivo", "Prejuízo", "Estoque"]}>
            {historico.map((m) => (
              <tr key={m.id}>
                <td className="muted-cell">{fmtDateTime(m.dataHora)}</td>
                <td><strong>{m.produtoNome}</strong>{m.observacao && <span className="cell-sub">{m.observacao}</span>}</td>
                <td>{m.loteCodigo ?? "—"}</td>
                <td className="text-bad">{fmtNum(Math.abs(m.quantidade))}</td>
                <td>{m.tipo === "AJUSTE" ? "Diferença negativa de balanço" : (MOTIVO_LABEL[m.motivo as MotivoDesperdicio] ?? m.motivo ?? "—")}
                  {m.descricaoMotivo && m.tipo !== "AJUSTE" && <span className="cell-sub">{m.descricaoMotivo}</span>}
                </td>
                <td className="text-bad">{fmtMoney(m.valorPrejuizo ?? 0)}</td>
                <td><StatusBadge label="Já baixado" tone="neutral" /></td>
              </tr>
            ))}
          </DataTable>
        ))}
      </Card>

      <Card title="Embalagens abertas">
        {erroAbertos && <AlertBanner tone="bad">{erroAbertos}</AlertBanner>}
        {loadingAbertos && !abertos ? <p className="muted">Carregando…</p> : !abertos?.length ? (
          <EmptyState title="Nenhuma embalagem aberta" text="Não há sobras de embalagens para descartar." icon="box-open" />
        ) : (
          <DataTable caption="Embalagens abertas na cozinha" headers={["Produto", "Lote / validade", "Aberta", "Resta", "Ação"]}>
            {abertos.map((p) => (
              <tr key={p.id}>
                <td><strong>{p.produtoNome}</strong></td>
                <td>Lote {p.loteCodigo} · vence {fmtDate(p.dataValidade)}</td>
                <td>{fmtDateTime(p.dataAbertura)}</td>
                <td>{fmtNum(p.quantidadeRestante)} {p.unidadeMedida.toLowerCase()}</td>
                <td><Button size="sm" variant="danger" onClick={() => setAlvo(p)}>Excluir do estoque</Button></td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      {loteDescarte && (
        <Modal open title={`Excluir do estoque: ${loteDescarte.produtoNome}`} onClose={() => setLoteDescarte(null)}>
          <p>Confirme a quantidade e o motivo. O descarte será registrado como desperdício.</p>
          <FormularioDesperdicio produtoInicial={String(loteDescarte.produtoId)} loteInicial={loteDescarte} onDone={() => {
            setLoteDescarte(null);
            recarregar();
            toast.success("Descarte registrado e estoque atualizado");
          }} />
        </Modal>
      )}
      {alvo && <AcaoProdutoAbertoModal tipo="desperdicar" item={alvo} onClose={() => setAlvo(null)} onDone={() => {
        setAlvo(null);
        recarregar();
        toast.success("Desperdício da embalagem registrado");
      }} />}
    </>
  );
}

function FormularioDesperdicio({
  produtoInicial,
  loteInicial,
  onDone,
}: {
  produtoInicial: string;
  loteInicial?: LoteDTO;
  onDone: () => void;
}) {
  const formId = useId();
  const [produtoId, setProdutoId] = useState(produtoInicial);
  const [quantidade, setQuantidade] = useState<number | null>(loteInicial?.quantidadeAtual ?? null);
  const [motivo, setMotivo] = useState<MotivoDesperdicio>(loteInicial && !loteInicial.vencido ? "DETERIORACAO" : "VENCIMENTO");
  const [descricaoMotivo, setDescricaoMotivo] = useState("");
  const [manualLote, setManualLote] = useState(!!loteInicial);
  const [loteId, setLoteId] = useState(loteInicial ? String(loteInicial.id) : "");
  const [observacao, setObservacao] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: estoque, loading: loadingEstoque, refresh: refreshEstoque } = useFetch<EstoqueDTO[]>("/api/estoque", [], true);
  const produtoOpts = useMemo(
    () =>
      (estoque ?? []).map((p) => ({
        value: String(p.produtoId),
        label: p.produtoNome,
        sub: `${p.categoriaNome} · ${p.unidadeMedida}`,
      })),
    [estoque]
  );
  const estoqueSelecionado = (estoque ?? []).find((p) => String(p.produtoId) === produtoId);

  const { data: lotes, loading: loadingLotes, refresh: refreshLotes } = useFetch<LoteDTO[]>(produtoId && manualLote ? `/api/lotes?produtoId=${produtoId}` : null, [], true);
  const { data: detalheProduto } = useFetch<ProdutoDTO>(produtoId ? `/api/produtos/${produtoId}` : null);

  // No desperdício vale todos os lotes, inclusive vencidos: boa parte da perda
  // é exatamente o que já passou da validade.
  const lotesDisponiveis = useMemo(() => (lotes ?? []).filter((l) => l.produtoId === Number(produtoId) && l.quantidadeAtual > 0), [lotes, produtoId]);
  const loteSelecionado = lotesDisponiveis.find((l) => String(l.id) === loteId);
  const saldoDisponivel = estoqueSelecionado?.saldoAtual ?? 0;

  const submit = async () => {
    if (!produtoId) return setError("Escolha o produto.");
    if (!quantidade || quantidade <= 0) return setError("Informe uma quantidade maior que zero.");
    if (manualLote && loteSelecionado && quantidade > (loteSelecionado.quantidadeAtual ?? 0)) {
      return setError(
        `No lote selecionado há apenas ${fmtNum(loteSelecionado.quantidadeAtual ?? 0)} ${loteSelecionado.unidadeMedida.toLowerCase()} disponíveis.`
      );
    }
    if (!manualLote && estoqueSelecionado && quantidade > saldoDisponivel) {
      return setError(
        `O saldo disponível é ${fmtNum(saldoDisponivel)} ${estoqueSelecionado.unidadeMedida.toLowerCase()}. Informe até ${fmtNum(saldoDisponivel)}.`
      );
    }
    if (motivo === "OUTRO" && !descricaoMotivo.trim())
      return setError("Explique o motivo do desperdício quando o motivo for Outro.");
    if (manualLote && !loteSelecionado) return setError("Escolha um lote com saldo para descartar.");
    if (loadingEstoque || loadingLotes) return setError("Aguarde a atualização do estoque.");
    setSubmitting(true);
    setError(null);
    const lote = lotesDisponiveis.find((l) => String(l.id) === loteId);
    try {
      await api.post<MovimentacaoResultadoDTO>("/api/desperdicios", {
        produtoId: Number(produtoId),
        loteId: lote?.id ?? null,
        versionLote: lote?.version ?? null,
        quantidade,
        motivo,
        descricaoMotivo: motivo === "OUTRO" ? descricaoMotivo : null,
        observacao: observacao || null,
      });
      await Promise.all([refreshEstoque(), refreshLotes()]);
      setQuantidade(null);
      setObservacao("");
      setDescricaoMotivo("");
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
      noValidate
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
          setQuantidade(null);
          setLoteId("");
          setError(null);
        }}
        placeholder="Buscar produto…"
        required
      />

      <div className="form-grid-2">
        <NumberField
          label="Quantidade descartada"
          htmlFor={`${formId}-quantidade`}
          value={quantidade}
          onValue={setQuantidade}
          min={0}
          max={manualLote ? loteSelecionado?.quantidadeAtual ?? 0 : saldoDisponivel}
          placeholder="Ex.: 2"
          suffix={detalheProduto?.unidadeMedida ?? "UN"}
          required
        />
        <Field label="Motivo" htmlFor={`${formId}-desp-motivo`} required>
          <Select
            id={`${formId}-desp-motivo`}
            value={motivo}
            onChange={(e) => setMotivo(e.target.value as MotivoDesperdicio)}
          >
            {MOTIVOS.map((m) => (
              <option key={m} value={m}>
                {MOTIVO_LABEL[m]}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {motivo === "OUTRO" && (
        <Field label="Descreva o motivo" htmlFor={`${formId}-desp-outro`} required>
          <Textarea
            id={`${formId}-desp-outro`}
            value={descricaoMotivo}
            onChange={(e) => setDescricaoMotivo(e.target.value)}
            placeholder="Ex.: suspeita de problema com a geladeira…"
          />
        </Field>
      )}

      <Checkbox
        checked={manualLote}
        onChange={(v) => {
          setManualLote(v);
          setLoteId("");
        }}
        label="Escolho o lote manualmente"
      />

      {manualLote && lotesDisponiveis.length > 0 && (
        <SearchSelect
          label="Lote descartado"
          options={lotesDisponiveis.map((l) => ({
            value: String(l.id),
            label: l.codigo,
            sub: `Validade ${l.dataValidade ?? "—"} · saldo ${fmtNum(l.quantidadeAtual)} ${l.unidadeMedida}${l.vencido ? " · vencido" : ""}`,
          }))}
          value={loteId}
          onValue={setLoteId}
          placeholder="Escolha o lote…"
        />
      )}
      {manualLote && lotesDisponiveis.length === 0 && (
        <AlertBanner tone="info">Este produto não tem lotes para escolha.</AlertBanner>
      )}

      {!manualLote && (
        <AlertBanner tone="info">
          Lote não informado: o sistema aplica a regra FIFO (primeiro que vence, primeiro que sai)
        </AlertBanner>
      )}

      <Field label="Observação (opcional)" htmlFor={`${formId}-desp-obs`}>
        <Input
          id={`${formId}-desp-obs`}
          value={observacao}
          onChange={(e) => setObservacao(e.target.value)}
          placeholder="Notas internas…"
        />
      </Field>

      <div className="form-actions">
        <Button type="submit" variant="danger" loading={submitting} disabled={loadingEstoque || loadingLotes || !produtoId || (manualLote ? !loteSelecionado : saldoDisponivel <= 0)}>
          {loteInicial ? "Confirmar exclusão do estoque" : "Registrar desperdício"}
        </Button>
      </div>
    </form>
  );
}
