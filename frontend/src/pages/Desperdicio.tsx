import { traduzir } from "../lib/i18n";
import { Paginacao } from "../components/Paginacao";
import { fmtLote } from "../lib/format";
import { mensagemErro } from "../lib/api";
import { useEffect, useId, useMemo, useState } from "react";
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
import { fmtNum, fmtDate, fmtDateTime, fmtMoney } from "../lib/format";
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
import { useAuth } from "../store/auth";
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
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [params] = useSearchParams();
  const produtoParam = params.get("produto") ?? "";
  const [alvo, setAlvo] = useState<ProdutoAbertoDTO | null>(null);
  const [loteDescarte, setLoteDescarte] = useState<LoteDTO | null>(null);
  const [revisao, setRevisao] = useState(0);
  const [paginaLotes,setPaginaLotes]=useState(0);
  const [tamanhoLotes,setTamanhoLotes]=useState(25);
  const [situacao, setSituacao] = useState("VENCIDOS");
  const [buscaLotes, setBuscaLotes] = useState("");
  const [buscaHistorico, setBuscaHistorico] = useState("");
  const [motivoFiltro, setMotivoFiltro] = useState("TODOS");
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");

  const { data: lotes, loading: loadingLotes, error: erroLotes, refresh: refreshLotes } =
    useFetch<import("../lib/types").Page<LoteDTO>>(isAdmin ? `/api/lotes/pagina?q=${encodeURIComponent(buscaLotes)}&vencidos=${situacao === "VENCIDOS"}&page=${paginaLotes}&size=${tamanhoLotes}` : null, [], true);
  const { data: abertos, loading: loadingAbertos, error: erroAbertos, refresh: refreshAbertos } =
    useFetch<ProdutoAbertoDTO[]>("/api/produtos-abertos?finalizado=false", [], true);

  const periodoInvalido = !!inicio && !!fim && inicio > fim;
  // O servidor converte as datas locais para um intervalo UTC com fim exclusivo.
  const { data: registros, loading: loadingRegistros, error: erroRegistros, refresh: refreshRegistros } =
    useFetch<MovimentacaoDTO[]>(periodoInvalido ? null :
      `/api/movimentacoes?tipo=DESPERDICIO&dataInicio=${inicio || "1970-01-01"}&dataFim=${fim}`, [], true);

  const lotesVisiveis=lotes?.content ?? [];
  useEffect(()=>setPaginaLotes(0),[buscaLotes,situacao,tamanhoLotes]);
  useEffect(()=>{if(lotes && paginaLotes>0 && paginaLotes>=lotes.totalPages)setPaginaLotes(Math.max(0,lotes.totalPages-1));},[lotes,paginaLotes]);
  const historico = useMemo(() => (registros ?? []).filter((m) => {
    const balanco = m.tipo === "AJUSTE";
    const motivo = balanco ? "BALANCO" : m.motivo;
    return (motivoFiltro === "TODOS" || motivoFiltro === motivo) &&
      `${m.produtoNome} ${m.loteCodigo ?? ""} ${m.loteFabricante ?? ""} ${m.descricaoMotivo ?? ""} ${m.observacao ?? ""}`
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
      <PageHeader title={traduzir("Desperdício")} subtitle={traduzir("Vencidos para descarte e todas as perdas da cozinha, incluindo diferenças negativas do balanço.")} />

      {isAdmin && <Card collapsible title={traduzir("Itens para excluir do estoque")} actions={
        <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refreshLotes()}>{" "}{traduzir("Atualizar")}{" "}</Button>
      }>
        <div className="form-grid-2">
          <Field label={traduzir("Situação")} htmlFor="desp-situacao">
            <Select id="desp-situacao" value={situacao} onChange={(e) => setSituacao(e.target.value)}>
              <option value="VENCIDOS">{" "}{traduzir("Lotes vencidos")}{" "}</option>
              <option value="TODOS">{" "}{traduzir("Todos os lotes com saldo")}{" "}</option>
            </Select>
          </Field>
          <Field label={traduzir("Buscar produto ou lote")} htmlFor="desp-busca-lotes">
            <Input id="desp-busca-lotes" value={buscaLotes} onChange={(e) => setBuscaLotes(e.target.value)} placeholder={traduzir("Nome do produto ou código…")} />
          </Field>
        </div>
        <p className="muted">{" "}{traduzir("Para descartar um produto deteriorado, escolha “Todos os lotes com saldo” e informe o motivo na exclusão.")}{" "}</p>
        {erroLotes && <AlertBanner tone="bad">{erroLotes}</AlertBanner>}
        {loadingLotes && !lotes ? <p className="muted">{" "}{traduzir("Carregando lotes…")}{" "}</p> : lotesVisiveis.length === 0 ? (
          <EmptyState title={situacao === "VENCIDOS" ? traduzir("Nenhum lote vencido com saldo") : traduzir("Nenhum lote encontrado")}
            text={traduzir("Os itens totalmente descartados ficam no histórico abaixo.")} icon="trash" />
        ) : (
          <DataTable caption={traduzir("Lotes com saldo para descarte")} headers={[traduzir("Produto"), traduzir("Lote"), traduzir("Validade"), traduzir("Quantidade"), traduzir("Valor"), traduzir("Situação"), traduzir("Ação")]}>
            {lotesVisiveis.map((l) => (
              <tr key={l.id}>
                <td><strong>{l.produtoNome}</strong></td>
                <td>{fmtLote(l.codigo, l.loteFabricante)}</td>
                <td className={l.vencido ? "text-bad" : ""}>{fmtDate(l.dataValidade)}</td>
                <td>{fmtNum(l.quantidadeAtual)} {l.unidadeMedida.toLowerCase()}</td>
                <td>{fmtMoney(l.quantidadeAtual * l.precoUnitario)}</td>
                <td><StatusBadge label={l.vencido ? traduzir("Vencido") : traduzir("No estoque")} tone={l.vencido ? "bad" : "neutral"} /></td>
                <td><Button size="sm" variant="danger" onClick={() => setLoteDescarte(l)}>{" "}{traduzir("Excluir do estoque")}{" "}</Button></td>
              </tr>
            ))}
          </DataTable>
        )}
        <Paginacao dados={lotes} pagina={paginaLotes} alterar={setPaginaLotes} itensPorPagina={tamanhoLotes} alterarTamanho={setTamanhoLotes} />
      </Card>}

      <Card title={traduzir("Registrar outra perda")}>
        <FormularioDesperdicio key={`${produtoParam}-${revisao}`} produtoInicial={produtoParam} onDone={() => {
          recarregar();
          toast.success(traduzir("Desperdício registrado e estoque atualizado"));
        }} />
      </Card>

      <Card title={traduzir("Histórico geral de desperdício")} actions={
        <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refreshRegistros()}>{" "}{traduzir("Atualizar")}{" "}</Button>
      }>
        <div className="form-grid-2">
          <Field label={traduzir("Buscar produto ou lote")} htmlFor="desp-busca-historico">
            <Input id="desp-busca-historico" value={buscaHistorico} onChange={(e) => setBuscaHistorico(e.target.value)} placeholder={traduzir("Buscar no histórico…")} />
          </Field>
          <Field label={traduzir("Motivo da perda")} htmlFor="desp-filtro-motivo">
            <Select id="desp-filtro-motivo" value={motivoFiltro} onChange={(e) => setMotivoFiltro(e.target.value)}>
              <option value="TODOS">{" "}{traduzir("Todos os motivos")}{" "}</option>
              {MOTIVOS.map((m) => <option key={m} value={m}>{traduzir(MOTIVO_LABEL[m])}</option>)}
              <option value="BALANCO">{" "}{traduzir("Diferença negativa de balanço")}{" "}</option>
            </Select>
          </Field>
          <Field label={traduzir("De (opcional)")} htmlFor="desp-inicio">
            <Input id="desp-inicio" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          </Field>
          <Field label={traduzir("Até (opcional)")} htmlFor="desp-fim">
            <Input id="desp-fim" type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
          </Field>
        </div>
        <p className="muted">{historico.length}{" "}{traduzir("registro(s) · Prejuízo:")}{" "}<strong>{fmtMoney(prejuizo)}</strong></p>
        <p className="muted">{" "}{traduzir("As perdas deste histórico já foram baixadas do estoque. Diferenças negativas entram quando os ajustes do balanço são aplicados.")}{" "}</p>
        {periodoInvalido && <AlertBanner tone="bad">{" "}{traduzir("A data inicial deve ser anterior ou igual à final.")}{" "}</AlertBanner>}
        {erroRegistros && <AlertBanner tone="bad">{erroRegistros}</AlertBanner>}
        {!periodoInvalido && (loadingRegistros && !registros ? <p className="muted">{" "}{traduzir("Carregando histórico…")}{" "}</p> : historico.length === 0 ? (
          <EmptyState title={traduzir("Nenhuma perda encontrada")} text={traduzir("Registre um descarte ou ajuste os filtros do histórico.")} icon="trash" />
        ) : (
          <DataTable caption={traduzir("Todos os desperdícios registrados")} headers={[traduzir("Quando"), traduzir("Produto"), traduzir("Lote"), traduzir("Quantidade"), traduzir("Motivo"), traduzir("Prejuízo"), traduzir("Estoque")]}>
            {historico.map((m) => (
              <tr key={m.id}>
                <td className="muted-cell">{fmtDateTime(m.dataHora)}</td>
                <td><strong>{m.produtoNome}</strong>{m.observacao && <span className="cell-sub">{m.observacao}</span>}</td>
                <td>{fmtLote(m.loteCodigo, m.loteFabricante)}</td>
                <td className="text-bad">{fmtNum(Math.abs(m.quantidade))} {m.unidadeMedida?.toLowerCase()}</td>
                <td>{m.tipo === "AJUSTE" ? traduzir("Diferença negativa de balanço") : traduzir(MOTIVO_LABEL[m.motivo as MotivoDesperdicio] ?? m.motivo ?? "—")}
                  {m.descricaoMotivo && m.tipo !== "AJUSTE" && <span className="cell-sub">{m.descricaoMotivo}</span>}
                </td>
                <td className="text-bad">{fmtMoney(m.valorPrejuizo ?? 0)}</td>
                <td><StatusBadge label={traduzir("Já baixado")} tone="neutral" /></td>
              </tr>
            ))}
          </DataTable>
        ))}
      </Card>

      <Card title={traduzir("Embalagens abertas")}>
        {erroAbertos && <AlertBanner tone="bad">{erroAbertos}</AlertBanner>}
        {loadingAbertos && !abertos ? <p className="muted">{" "}{traduzir("Carregando…")}{" "}</p> : !abertos?.length ? (
          <EmptyState title={traduzir("Nenhuma embalagem aberta")} text={traduzir("Não há sobras de embalagens para descartar.")} icon="box-open" />
        ) : (
          <DataTable caption={traduzir("Embalagens abertas na cozinha")} headers={[traduzir("Produto"), traduzir("Lote / validade"), traduzir("Aberta"), traduzir("Resta"), traduzir("Ação")]}>
            {abertos.map((p) => (
              <tr key={p.id}>
                <td><strong>{p.produtoNome}</strong></td>
                <td>{" "}{traduzir("Lote")}{" "}{fmtLote(p.loteCodigo, p.loteFabricante)}{" "}{traduzir("· vence")}{" "}{fmtDate(p.dataValidade)}</td>
                <td>{fmtDateTime(p.dataAbertura)}</td>
                <td>{fmtNum(p.quantidadeRestante)} {p.unidadeMedida.toLowerCase()}</td>
                <td><Button size="sm" variant="danger" onClick={() => setAlvo(p)}>{" "}{traduzir("Registrar desperdício")}{" "}</Button></td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      {isAdmin && loteDescarte && (
        <Modal open title={traduzir("Excluir do estoque: {0}",{0:loteDescarte.produtoNome})} onClose={() => setLoteDescarte(null)}>
          <p>{" "}{traduzir("Confirme a quantidade e o motivo. O descarte será registrado como desperdício.")}{" "}</p>
          <FormularioDesperdicio produtoInicial={String(loteDescarte.produtoId)} loteInicial={loteDescarte} onDone={() => {
            setLoteDescarte(null);
            recarregar();
            toast.success(traduzir("Descarte registrado e estoque atualizado"));
          }} />
        </Modal>
      )}
      {alvo && <AcaoProdutoAbertoModal tipo="desperdicar" item={alvo} onClose={() => setAlvo(null)} onDone={() => {
        setAlvo(null);
        recarregar();
        toast.success(traduzir("Desperdício da embalagem registrado"));
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
    if (!produtoId) return setError(traduzir("Escolha o produto."));
    if (!quantidade || quantidade <= 0) return setError(traduzir("Informe uma quantidade maior que zero."));
    if (manualLote && loteSelecionado && quantidade > (loteSelecionado.quantidadeAtual ?? 0)) {
      return setError(
        traduzir("No lote selecionado há apenas {0} {1} disponíveis.",{0:fmtNum(loteSelecionado.quantidadeAtual ?? 0),1:loteSelecionado.unidadeMedida.toLowerCase()})
      );
    }
    if (!manualLote && estoqueSelecionado && quantidade > saldoDisponivel) {
      return setError(
        traduzir("O saldo disponível é {0} {1}. Informe até {2}.",{0:fmtNum(saldoDisponivel),1:estoqueSelecionado.unidadeMedida.toLowerCase(),2:fmtNum(saldoDisponivel)})
      );
    }
    if (motivo === "OUTRO" && !descricaoMotivo.trim())
      return setError(traduzir("Explique o motivo do desperdício quando o motivo for Outro."));
    if (manualLote && !loteSelecionado) return setError(traduzir("Escolha um lote com saldo para descartar."));
    if (loadingEstoque || loadingLotes) return setError(traduzir("Aguarde a atualização do estoque."));
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
      setError(mensagemErro(e));
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
          {traduzir(error)}
        </div>
      )}

      <SearchSelect
        label={traduzir("Produto")}
        options={produtoOpts}
        value={produtoId}
        onValue={(v) => {
          setProdutoId(v);
          setQuantidade(null);
          setLoteId("");
          setError(null);
        }}
        placeholder={traduzir("Buscar produto…")}
        required
      />

      <p role="status" className="muted">{" "}{traduzir("Saldo disponível:")}{" "}<strong>{fmtNum(manualLote ? loteSelecionado?.quantidadeAtual ?? 0 : saldoDisponivel)} {estoqueSelecionado?.unidadeMedida.toLowerCase() ?? detalheProduto?.unidadeMedida.toLowerCase()}</strong></p>
      <div className="form-grid-2">
        <NumberField
          label={traduzir("Quantidade descartada")}
          htmlFor={`${formId}-quantidade`}
          value={quantidade}
          onValue={setQuantidade}
          min={0}
          max={manualLote ? loteSelecionado?.quantidadeAtual ?? 0 : saldoDisponivel}
          placeholder={traduzir("Ex.: 2")}
          suffix={detalheProduto?.unidadeMedida ?? "UN"}
          required
        />
        <Field label={traduzir("Motivo")} htmlFor={`${formId}-desp-motivo`} required>
          <Select
            id={`${formId}-desp-motivo`}
            value={motivo}
            onChange={(e) => setMotivo(e.target.value as MotivoDesperdicio)}
          >
            {MOTIVOS.map((m) => (
              <option key={m} value={m}>
                {traduzir(MOTIVO_LABEL[m])}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {motivo === "OUTRO" && (
        <Field label={traduzir("Descreva o motivo")} htmlFor={`${formId}-desp-outro`} required>
          <Textarea
            id={`${formId}-desp-outro`}
            value={descricaoMotivo}
            onChange={(e) => setDescricaoMotivo(e.target.value)}
            placeholder={traduzir("Ex.: suspeita de problema com a geladeira…")}
          />
        </Field>
      )}

      <Checkbox
        checked={manualLote}
        onChange={(v) => {
          setManualLote(v);
          setLoteId("");
        }}
        label={traduzir("Escolho o lote manualmente")}
      />

      {manualLote && lotesDisponiveis.length > 0 && (
        <SearchSelect
          label={traduzir("Lote descartado")}
          options={lotesDisponiveis.map((l) => ({
            value: String(l.id),
            label: l.codigo,
            sub: `Validade ${l.dataValidade ?? "—"} · saldo ${fmtNum(l.quantidadeAtual)} ${l.unidadeMedida}${l.vencido ? " · vencido" : ""}`,
          }))}
          value={loteId}
          onValue={setLoteId}
          placeholder={traduzir("Escolha o lote…")}
        />
      )}
      {manualLote && lotesDisponiveis.length === 0 && (
        <AlertBanner tone="info">{" "}{traduzir("Este produto não tem lotes para escolha.")}{" "}</AlertBanner>
      )}

      {!manualLote && (
        <AlertBanner tone="info">
          {" "}{traduzir("Lote não informado: o sistema aplica a regra FIFO (primeiro que vence, primeiro que sai)")}{" "}</AlertBanner>
      )}

      <Field label={traduzir("Observação (opcional)")} htmlFor={`${formId}-desp-obs`}>
        <Input
          id={`${formId}-desp-obs`}
          value={observacao}
          onChange={(e) => setObservacao(e.target.value)}
          placeholder={traduzir("Notas internas…")}
        />
      </Field>

      <div className="form-actions">
        <Button type="submit" variant="danger" loading={submitting} disabled={loadingEstoque || loadingLotes || !produtoId || (manualLote ? !loteSelecionado : saldoDisponivel <= 0)}>
          {loteInicial ? traduzir("Confirmar exclusão do estoque") : traduzir("Registrar desperdício")}
        </Button>
      </div>
    </form>
  );
}
