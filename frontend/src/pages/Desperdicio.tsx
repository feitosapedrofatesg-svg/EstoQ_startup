import { useMemo, useState } from "react";
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
} from "../components/UI";
import { fmtNum, fmtDate, fmtDateTime, fmtMoney, hojeISO } from "../lib/format";
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

/** Desperdício: o que se perdeu, com o motivo que explica a perda. */
export function Desperdicio() {
  const toast = useToast();
  const [params] = useSearchParams();
  // Deep link do Estoque: /desperdicio?produto=123 já abre com o produto escolhido.
  const produtoParam = params.get("produto") ?? "";
  const [alvo, setAlvo] = useState<ProdutoAbertoDTO | null>(null);

  const {
    data: abertos,
    loading: loadingAbertos,
    error: erroAbertos,
    refresh: refreshAbertos,
  } = useFetch<ProdutoAbertoDTO[]>("/api/produtos-abertos?finalizado=false");

  const hoje = hojeISO();
  const { data: registros, loading: loadingRegistros, refresh: refreshRegistros } =
    useFetch<MovimentacaoDTO[]>(
      `/api/movimentacoes?tipo=DESPERDICIO&inicio=${hoje}T00:00:00&fim=${hoje}T23:59:59`
    );

  const prejuizoHoje = useMemo(
    () => (registros ?? []).reduce((acc, m) => acc + (m.valorPrejuizo ?? 0), 0),
    [registros]
  );

  const recarregar = () => {
    void refreshAbertos();
    void refreshRegistros();
  };

  return (
    <>
      <PageHeader
        title="Desperdício"
        subtitle="O que foi para o lixo, com o motivo da perda para o CMV fechar certo."
      />

      <Card title="Novo desperdício">
        <FormularioDesperdicio
          produtoInicial={produtoParam}
          onDone={() => {
            recarregar();
            toast.success("Desperdício registrado");
          }}
        />
      </Card>

      <Card title="Embalagens abertas">
        {erroAbertos && <div className="alertbanner alertbanner--bad">{erroAbertos}</div>}
        {loadingAbertos && !abertos ? (
          <p className="muted">Carregando…</p>
        ) : abertos && abertos.length === 0 ? (
          <EmptyState
            title="Nenhuma embalagem aberta"
            text="Não há sobras de embalagens para descartar. Abra o que for usado na tela de Consumo."
            icon="box-open"
          />
        ) : (
          <DataTable
            caption="Embalagens abertas na cozinha"
            headers={["Produto", "Lote / validade", "Aberta", "Já usado", "Resta", "Ações"]}
          >
            {abertos?.map((p) => (
              <tr key={p.id}>
                <td>
                  <strong>{p.produtoNome}</strong>
                </td>
                <td>
                  <span className="cell-sub">
                    Lote {p.loteCodigo} · vence {fmtDate(p.dataValidade)}
                  </span>
                </td>
                <td className="muted-cell">{fmtDateTime(p.dataAbertura)}</td>
                <td>
                  {fmtNum(p.quantidadeUtilizada)} {p.unidadeMedida.toLowerCase()}
                </td>
                <td>
                  <strong>
                    {fmtNum(p.quantidadeRestante)} {p.unidadeMedida.toLowerCase()}
                  </strong>
                </td>
                <td>
                  <div className="td-actions">
                    <Button size="sm" variant="danger" onClick={() => setAlvo(p)}>
                      Desperdiçar
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      <Card
        title="Desperdício de hoje"
        actions={
          <div className="filters">
            <span className="muted">
              Prejuízo: <strong>{fmtMoney(prejuizoHoje)}</strong>
            </span>
            <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refreshRegistros()}>
              Atualizar
            </Button>
          </div>
        }
      >
        {loadingRegistros && !registros ? (
          <p className="muted">Carregando…</p>
        ) : registros && registros.length === 0 ? (
          <EmptyState
            title="Nenhum desperdício registrado hoje"
            text="Use o formulário acima para registrar o que foi perdido."
            icon="trash"
          />
        ) : (
          <DataTable
            caption="Desperdícios registrados hoje"
            headers={["Quando", "Produto", "Lote", "Quantidade", "Motivo", "Prejuízo"]}
          >
            {registros?.map((m) => (
              <tr key={m.id}>
                <td className="muted-cell">{fmtDateTime(m.dataHora)}</td>
                <td>
                  <strong>{m.produtoNome}</strong>
                  {m.observacao && <span className="cell-sub">{m.observacao}</span>}
                </td>
                <td className="muted-cell">{m.loteCodigo ?? "—"}</td>
                <td className="text-bad">{fmtNum(m.quantidade)}</td>
                <td className="muted-cell">
                  {m.motivo ? (MOTIVO_LABEL[m.motivo as MotivoDesperdicio] ?? m.motivo) : "—"}
                </td>
                <td className="text-bad">{fmtMoney(m.valorPrejuizo ?? 0)}</td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      {alvo && (
        <AcaoProdutoAbertoModal
          tipo="desperdicar"
          item={alvo}
          onClose={() => setAlvo(null)}
          onDone={() => {
            setAlvo(null);
            recarregar();
            toast.success("Desperdício da embalagem registrado");
          }}
        />
      )}
    </>
  );
}

function FormularioDesperdicio({
  produtoInicial,
  onDone,
}: {
  produtoInicial: string;
  onDone: () => void;
}) {
  const [produtoId, setProdutoId] = useState(produtoInicial);
  const [quantidade, setQuantidade] = useState<number | null>(null);
  const [motivo, setMotivo] = useState<MotivoDesperdicio>("VENCIMENTO");
  const [descricaoMotivo, setDescricaoMotivo] = useState("");
  const [manualLote, setManualLote] = useState(false);
  const [loteId, setLoteId] = useState("");
  const [observacao, setObservacao] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: estoque } = useFetch<EstoqueDTO[]>("/api/estoque");
  const produtoOpts = useMemo(
    () =>
      (estoque ?? []).map((p) => ({
        value: String(p.produtoId),
        label: p.produtoNome,
        sub: `${p.categoriaNome} · ${p.unidadeMedida}`,
      })),
    [estoque]
  );

  const { data: lotes } = useFetch<LoteDTO[]>(produtoId && manualLote ? `/api/lotes?produtoId=${produtoId}` : null);
  const { data: detalheProduto } = useFetch<ProdutoDTO>(produtoId ? `/api/produtos/${produtoId}` : null);

  // No desperdício vale todos os lotes, inclusive vencidos: boa parte da perda
  // é exatamente o que já passou da validade.
  const lotesDisponiveis = useMemo(() => lotes ?? [], [lotes]);

  const submit = async () => {
    if (!produtoId) return setError("Escolha o produto.");
    if (!quantidade || quantidade <= 0) return setError("Informe uma quantidade maior que zero.");
    if (motivo === "OUTRO" && !descricaoMotivo.trim())
      return setError("Explique o motivo do desperdício quando o motivo for Outro.");
    if (manualLote && !loteId) return setError("Escolha o lote descartado.");
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
          setLoteId("");
          setError(null);
        }}
        placeholder="Buscar produto…"
        required
      />

      <div className="form-grid-2">
        <NumberField
          label="Quantidade descartada"
          value={quantidade}
          onValue={setQuantidade}
          min={0}
          placeholder="Ex.: 2"
          suffix={detalheProduto?.unidadeMedida ?? "UN"}
          required
        />
        <Field label="Motivo" htmlFor="desp-motivo" required>
          <Select
            id="desp-motivo"
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
        <Field label="Descreva o motivo" htmlFor="desp-outro" required>
          <Textarea
            id="desp-outro"
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

      <Field label="Observação (opcional)" htmlFor="desp-obs">
        <Input
          id="desp-obs"
          value={observacao}
          onChange={(e) => setObservacao(e.target.value)}
          placeholder="Notas internas…"
        />
      </Field>

      <div className="form-actions">
        <Button type="submit" variant="danger" loading={submitting}>
          Registrar desperdício
        </Button>
      </div>
    </form>
  );
}