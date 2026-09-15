import { useMemo, useState } from "react";
import { useAuth } from "../store/auth";
import { useFetch } from "../lib/hooks";
import { api } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  Modal,
  NumberField,
  SearchSelect,
  Field,
  Input,
  DataTable,
  EmptyState,
  AlertBanner,
} from "../components/UI";
import { fmtNum, fmtDateTime, fmtDate } from "../lib/format";
import type { EstoqueDTO, LoteDTO, ProdutoAbertoDTO } from "../lib/types";
import { useToast } from "../store/toast";

export function ProdutosAbertos() {
  const { canMove } = useAuth();
  const toast = useToast();
  const { data, loading, error, refresh } = useFetch<ProdutoAbertoDTO[]>(
    "/api/produtos-abertos?finalizado=false"
  );
  const { data: estoque } = useFetch<EstoqueDTO[]>("/api/estoque");
  const [showOpen, setShowOpen] = useState(false);
  const [action, setAction] = useState<{ tipo: "consumir" | "desperdicar"; item: ProdutoAbertoDTO } | null>(
    null
  );

  const produtoOpts = useMemo(
    () =>
      (estoque ?? []).map((p) => ({
        value: String(p.produtoId),
        label: p.produtoNome,
        sub: `${p.categoriaNome} · ${p.unidadeMedida}`,
      })),
    [estoque]
  );

  return (
    <>
      <PageHeader
        title="Embalagens abertas"
        subtitle="Itens já abertos na cozinha, com quanto falta usar de cada um."
        actions={
          canMove && (
            <Button icon="box-open" onClick={() => setShowOpen(true)}>
              Abrir embalagem
            </Button>
          )
        }
      />

      <Card title="Em aberto agora">
        {error && <div className="alertbanner alertbanner--bad">{error}</div>}
        {loading && !data && <p className="muted">Carregando…</p>}
        {data && data.length === 0 ? (
          <EmptyState
            title="Nenhuma embalagem aberta"
            text="Ao abrir um pacote, bolsa ou vasilhame, registre aqui para controlar o aproveitamento."
            icon="box-open"
            action={
              canMove ? (
                <Button variant="outline" onClick={() => setShowOpen(true)}>
                  Abrir a primeira
                </Button>
              ) : (
                <p className="muted">O perfil Cozinha faz a abertura de embalagens.</p>
              )
            }
          />
        ) : (
          <DataTable
            caption="Embalagens abertas na cozinha"
            headers={["Produto", "Lote / validade", "Aberta", "Já usado", "Resta", "Ações"]}
          >
            {data?.map((p) => (
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
                    <Button size="sm" variant="outline" onClick={() => setAction({ tipo: "consumir", item: p })}>
                      Consumir
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => setAction({ tipo: "desperdicar", item: p })}>
                      Desperdiçar
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      {showOpen && (
        <AbrirEmbalagem produtoOpts={produtoOpts} onClose={() => setShowOpen(false)} onDone={() => {
          setShowOpen(false);
          void refresh();
          toast.success("Embalagem aberta e lançada");
        }} />
      )}

      {action && (
        <AcaoProdutoAberto
          tipo={action.tipo}
          item={action.item}
          onClose={() => setAction(null)}
          onDone={() => {
            setAction(null);
            void refresh();
            toast.success(action.tipo === "consumir" ? "Consumo da embalagem registrado" : "Desperdício da embalagem registrado");
          }}
        />
      )}
    </>
  );
}

function AbrirEmbalagem({
  produtoOpts,
  onClose,
  onDone,
}: {
  produtoOpts: { value: string; label: string; sub?: string }[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [produtoId, setProdutoId] = useState("");
  const [loteId, setLoteId] = useState("");
  const [qtdEmbalagem, setQtdEmbalagem] = useState<number | null>(null);
  const [qtdUsada, setQtdUsada] = useState<number | null>(null);
  const [observacao, setObservacao] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: lotes } = useFetch<LoteDTO[]>(produtoId ? `/api/lotes?produtoId=${produtoId}` : null);
  const lotesDisponiveis = useMemo(() => (lotes ?? []).filter((l) => l.disponivel && !l.vencido), [lotes]);

  const submit = async () => {
    if (!produtoId) return setError("Escolha o produto.");
    if (!loteId) return setError("Escolha o lote que será aberto.");
    if (!qtdEmbalagem || qtdEmbalagem <= 0) return setError("Informe o tamanho da embalagem.");
    if (qtdUsada === null || qtdUsada < 0) return setError("Informe quanto foi usado agora (pode ser zero).");
    setSubmitting(true);
    setError(null);
    const lote = lotesDisponiveis.find((l) => String(l.id) === loteId);
    try {
      await api.post("/api/produtos-abertos/abrir", {
        produtoId: Number(produtoId),
        loteId: Number(loteId),
        versionLote: lote?.version ?? null,
        quantidadeDaEmbalagem: qtdEmbalagem,
        quantoUsouAgora: qtdUsada,
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
      title="Abrir embalagem"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button onClick={() => void submit()} loading={submitting} icon="check">
            Abrir e lançar
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
          setLoteId("");
        }}
        placeholder="Buscar produto…"
        required
      />
      {lotesDisponiveis.length > 0 && (
        <SearchSelect
          label="Lote a abrir"
          options={lotesDisponiveis.map((l) => ({
            value: String(l.id),
            label: l.codigo,
            sub: `Validade ${l.dataValidade ?? "—"} · saldo ${fmtNum(l.quantidadeAtual)} ${l.unidadeMedida}`,
          }))}
          value={loteId}
          onValue={setLoteId}
          placeholder="Escolha o lote…"
          required
        />
      )}
      {produtoId && lotesDisponiveis.length === 0 && (
        <AlertBanner tone="warn">Este produto não tem lotes disponíveis para abrir.</AlertBanner>
      )}
      <div className="form-grid-2">
        <NumberField label="Tamanho da embalagem" value={qtdEmbalagem} onValue={setQtdEmbalagem} min={0} placeholder="Ex.: 1" hint="Ex.: 1 kg, 5 L, pacote com 30" />
        <NumberField label="Quanto usou ao abrir" value={qtdUsada} onValue={setQtdUsada} min={0} placeholder="Ex.: 0,2" hint="O restante fica na embalagem" />
      </div>
      <Field label="Observação (opcional)" htmlFor="abrir-obs">
        <Input id="abrir-obs" value={observacao} onChange={(e) => setObservacao(e.target.value)} placeholder="Notas internas…" />
      </Field>
    </Modal>
  );
}

function AcaoProdutoAberto({
  tipo,
  item,
  onClose,
  onDone,
}: {
  tipo: "consumir" | "desperdicar";
  item: ProdutoAbertoDTO;
  onClose: () => void;
  onDone: () => void;
}) {
  const [quantidade, setQuantidade] = useState<number | null>(null);
  const [observacao, setObservacao] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (!quantidade || quantidade <= 0) {
      setError("Informe uma quantidade maior que zero.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const body = {
        quantidade,
        version: item.version,
        versionLote: null,
        observacao: observacao || null,
      };
      if (tipo === "consumir") {
        await api.post(`/api/produtos-abertos/${item.id}/consumir`, body);
      } else {
        await api.post(`/api/produtos-abertos/${item.id}/desperdicar`, body);
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
      title={tipo === "consumir" ? `Consumir de ${item.produtoNome}` : `Desperdiçar ${item.produtoNome}`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button variant={tipo === "desperdicar" ? "danger" : "accent"} onClick={() => void submit()} loading={submitting}>
            {tipo === "consumir" ? "Consumir" : "Registrar desperdício"}
          </Button>
        </>
      }
    >
      <p className="muted" style={{ marginBottom: 16 }}>
        Embalagem de lote {item.loteCodigo}. Restam {fmtNum(item.quantidadeRestante)} {item.unidadeMedida.toLowerCase()}.
      </p>
      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          {error}
        </div>
      )}
      <NumberField
        label="Quantidade a registrar"
        value={quantidade}
        onValue={setQuantidade}
        min={0}
        max={item.quantidadeRestante}
        placeholder="Ex.: 0,5"
        suffix={item.unidadeMedida.toLowerCase()}
      />
      <Field label="Observação (opcional)" htmlFor="pa-obs">
        <Input id="pa-obs" value={observacao} onChange={(e) => setObservacao(e.target.value)} placeholder="Notas internas…" />
      </Field>
    </Modal>
  );
}