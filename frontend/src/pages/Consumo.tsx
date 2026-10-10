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
  ProdutoAbertoDTO,
  ProdutoDTO,
} from "../lib/types";
import { useToast } from "../store/toast";
import { AbrirEmbalagemModal, AcaoProdutoAbertoModal } from "../components/ProdutoAbertoModals";

/** Consumo do estoque: abertura de embalagem, baixa do que foi usado e histórico do dia. */
export function Consumo() {
  const toast = useToast();
  const [params] = useSearchParams();
  // Deep link do Estoque: /consumo?produto=123 já abre com o produto escolhido.
  const produtoParam = params.get("produto") ?? "";
  const [showOpen, setShowOpen] = useState(false);
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
      `/api/movimentacoes?tipo=CONSUMO&inicio=${hoje}T00:00:00&fim=${hoje}T23:59:59`
    );

  const custoHoje = useMemo(
    () => (registros ?? []).reduce((acc, m) => acc + (m.custoConsumo ?? 0), 0),
    [registros]
  );

  const recarregar = () => {
    void refreshAbertos();
    void refreshRegistros();
  };

  return (
    <>
      <PageHeader
        title="Consumo"
        subtitle="O que a cozinha usou do estoque. Abra a embalagem e registre a baixa."
        actions={
          <Button icon="box-open" onClick={() => setShowOpen(true)}>
            Abrir embalagem
          </Button>
        }
      />

      <Card title="Novo consumo">
        <FormularioConsumo
          produtoInicial={produtoParam}
          onDone={() => {
            recarregar();
            toast.success("Consumo registrado");
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
            text="Ao abrir um pacote, bolsa ou vasilhame, registre aqui para controlar o aproveitamento."
            icon="box-open"
            action={
              <Button variant="outline" icon="box-open" onClick={() => setShowOpen(true)}>
                Abrir a primeira
              </Button>
            }
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
                    <Button size="sm" variant="outline" onClick={() => setAlvo(p)}>
                      Consumir
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      <Card
        title="Consumo de hoje"
        actions={
          <div className="filters">
            <span className="muted">
              Custo registrado: <strong>{fmtMoney(custoHoje)}</strong>
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
            title="Nenhum consumo registrado hoje"
            text="Use o formulário acima para registrar o que foi usado."
            icon="clipboard-list"
          />
        ) : (
          <DataTable caption="Consumos registrados hoje" headers={["Quando", "Produto", "Lote", "Quantidade", "Custo"]}>
            {registros?.map((m) => (
              <tr key={m.id}>
                <td className="muted-cell">{fmtDateTime(m.dataHora)}</td>
                <td>
                  <strong>{m.produtoNome}</strong>
                  {m.observacao && <span className="cell-sub">{m.observacao}</span>}
                </td>
                <td className="muted-cell">{m.loteCodigo ?? "—"}</td>
                <td>{fmtNum(m.quantidade)}</td>
                <td>{fmtMoney(m.custoConsumo ?? 0)}</td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      {showOpen && (
        <AbrirEmbalagemModal
          onClose={() => setShowOpen(false)}
          onDone={() => {
            setShowOpen(false);
            recarregar();
            toast.success("Embalagem aberta e lançada");
          }}
        />
      )}

      {alvo && (
        <AcaoProdutoAbertoModal
          tipo="consumir"
          item={alvo}
          onClose={() => setAlvo(null)}
          onDone={() => {
            setAlvo(null);
            recarregar();
            toast.success("Consumo da embalagem registrado");
          }}
        />
      )}
    </>
  );
}

function FormularioConsumo({
  produtoInicial,
  onDone,
}: {
  produtoInicial: string;
  onDone: () => void;
}) {
  const [produtoId, setProdutoId] = useState(produtoInicial);
  const [quantidade, setQuantidade] = useState<number | null>(null);
  const [manualLote, setManualLote] = useState(false);
  const [loteId, setLoteId] = useState("");
  const [observacao, setObservacao] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: estoque, refresh: refreshEstoque } = useFetch<EstoqueDTO[]>("/api/estoque");
  const produtoOpts = useMemo(
    () =>
      (estoque ?? []).map((p) => ({
        value: String(p.produtoId),
        label: p.produtoNome,
        sub: `${p.categoriaNome} · consumível: ${fmtNum(p.saldoDisponivelConsumo ?? 0)} ${p.unidadeMedida.toLowerCase()}`,
      })),
    [estoque]
  );
  const estoqueSelecionado = (estoque ?? []).find((p) => String(p.produtoId) === produtoId);
  const saldoDisponivel = estoqueSelecionado?.saldoDisponivelConsumo ?? 0;

  const { data: lotes, refresh: refreshLotes } = useFetch<LoteDTO[]>(produtoId && manualLote ? `/api/lotes?produtoId=${produtoId}` : null);
  const { data: detalheProduto } = useFetch<ProdutoDTO>(produtoId ? `/api/produtos/${produtoId}` : null);

  const lotesDisponiveis = useMemo(
    () => (lotes ?? []).filter((l) => l.disponivel && !l.vencido),
    [lotes]
  );

  const loteSelecionado = lotesDisponiveis.find((l) => String(l.id) === loteId);
  const limiteQuantidade = manualLote ? (loteSelecionado?.quantidadeAtual ?? 0) : saldoDisponivel;

  const submit = async () => {
    if (!produtoId) return setError("Escolha o produto.");
    if (!quantidade || quantidade <= 0) return setError("Informe uma quantidade maior que zero.");
    if (manualLote && !loteSelecionado) return setError("Escolha um lote disponível.");
    if (quantidade > limiteQuantidade) {
      return setError(`A quantidade disponível é ${fmtNum(limiteQuantidade)} ${estoqueSelecionado?.unidadeMedida.toLowerCase() ?? "un"}.`);
    }
    setSubmitting(true);
    setError(null);
    const lote = lotesDisponiveis.find((l) => String(l.id) === loteId);
    try {
      await api.post<MovimentacaoResultadoDTO>("/api/consumos", {
        produtoId: Number(produtoId),
        loteId: lote?.id ?? null,
        versionLote: lote?.version ?? null,
        quantidade,
        observacao: observacao || null,
      });
      await Promise.all([refreshEstoque(), refreshLotes()]);
      setQuantidade(null);
      setObservacao("");
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
        hint={produtoId
          ? `Disponível no estoque: ${fmtNum(saldoDisponivel)} ${estoqueSelecionado?.unidadeMedida.toLowerCase() ?? "UN"}`
          : "Selecione um produto para ver o saldo disponível."}
        required
      />

      <div className="form-grid-2">
        <NumberField
          label="Quantidade consumida"
          value={quantidade}
          onValue={setQuantidade}
          min={0}
          max={limiteQuantidade}
          placeholder={produtoId ? "Ex.: até " + fmtNum(limiteQuantidade) : "Selecione o produto"}
          suffix={detalheProduto?.unidadeMedida ?? "UN"}
          hint={produtoId
            ? `Até ${fmtNum(limiteQuantidade)} ${detalheProduto?.unidadeMedida.toLowerCase() ?? "UN"} disponíveis.`
            : "O limite será preenchido após escolher o produto."}
          required
        />
        <Field label="Observação (opcional)" htmlFor="consumo-obs">
          <Input
            id="consumo-obs"
            value={observacao}
            onChange={(e) => setObservacao(e.target.value)}
            placeholder="Notas internas…"
          />
        </Field>
      </div>

      {produtoId && saldoDisponivel === 0 && (
        <AlertBanner tone="warn">
          Este produto não tem estoque disponível para consumir.
        </AlertBanner>
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
          label="Lote consumido"
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

      {!manualLote && (
        <AlertBanner tone="info">
          Lote não informado: o sistema aplica a regra FIFO (primeiro que vence, primeiro que sai)
        </AlertBanner>
      )}

      <div className="form-actions">
        <Button
          type="submit"
          loading={submitting}
          icon="check"
          disabled={!produtoId || limiteQuantidade <= 0}
        >
          Registrar consumo
        </Button>
      </div>
    </form>
  );
}