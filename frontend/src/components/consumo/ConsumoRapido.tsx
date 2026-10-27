import { mensagemErro } from "../../lib/api";
import { useMemo, useRef, useState } from "react";
import { useFetch } from "../../lib/hooks";
import { api, ApiError } from "../../lib/api";
import { fmtNum, hojeISO } from "../../lib/format";
import type { ConsumoLoteResultadoDTO, EstoqueDTO, LoteDTO } from "../../lib/types";
import { useAuth } from "../../store/auth";
import { Badge, Button, Checkbox, DataTable, EmptyState, Field, Modal, Select } from "../UI";
import { PainelQuantidade } from "./PainelQuantidade";
import { imprimirConsumo } from "./ImprimirConsumo";
import { TextoPorToque } from "./TextoPorToque";

interface Escolha { quantidade: number; loteId: string; versionLote: number | null; saldoLote: number | null; observacao: string }
interface ItemConferencia extends Escolha { produto: EstoqueDTO }
const vazio = (): Escolha => ({ quantidade: 0, loteId: "", versionLote: null, saldoLote: null, observacao: "" });
const passos = (unidade: string) => unidade === "UN" ? [1, 2, 5] : unidade === "KG" || unidade === "L" ? [0.1, 0.5, 1] : [50, 100, 500];

export function ConsumoRapido({ produtoInicial, onDone }: { produtoInicial: string; onDone: (resultado: ConsumoLoteResultadoDTO) => void }) {
  const { user } = useAuth();
  const { data: estoque, loading, error, refresh, setData } = useFetch<EstoqueDTO[]>("/api/estoque");
  const [escolhas, setEscolhas] = useState<Record<number, Escolha>>({});
  const [busca, setBusca] = useState("");
  const [categoria, setCategoria] = useState("");
  const [somenteSelecionados, setSomenteSelecionados] = useState(false);
  const [painel, setPainel] = useState<EstoqueDTO | null>(null);
  const [detalhe, setDetalhe] = useState<EstoqueDTO | null>(null);
  const [conferindo, setConferindo] = useState(false);
  const [revisao, setRevisao] = useState<ItemConferencia[]>([]);
  const [enviando, setEnviando] = useState(false);
  const bloqueio = useRef(false);
  const tentativa = useRef<{ conteudo: string; chave: string } | null>(null);
  const [incerto, setIncerto] = useState(false);
  const [erro, setErro] = useState("");
  const { data: lotes, loading: carregandoLotes } = useFetch<LoteDTO[]>(detalhe ? `/api/lotes?produtoId=${detalhe.produtoId}` : null);
  const selecionados = useMemo(() => (estoque ?? []).filter(p => (escolhas[p.produtoId]?.quantidade ?? 0) > 0), [estoque, escolhas]);
  const categorias = useMemo(() => [...new Set((estoque ?? []).map(p => p.categoriaNome))].sort(), [estoque]);
  const visiveis = useMemo(() => (estoque ?? []).filter(p =>
    (!categoria || p.categoriaNome === categoria) && (!somenteSelecionados || (escolhas[p.produtoId]?.quantidade ?? 0) > 0)
    && p.produtoNome.toLocaleLowerCase("pt-BR").includes(busca.toLocaleLowerCase("pt-BR")))
    .sort((a, b) => Number(String(b.produtoId) === produtoInicial) - Number(String(a.produtoId) === produtoInicial)), [estoque, categoria, somenteSelecionados, escolhas, busca, produtoInicial]);

  const mudar = (id: number, alteracao: Partial<Escolha>) => {
    if (bloqueio.current || incerto) return;
    setEscolhas(anterior => ({ ...anterior, [id]: { ...vazio(), ...anterior[id], ...alteracao } }));
    setErro("");
  };
  const limite = (produto: EstoqueDTO) => escolhas[produto.produtoId]?.loteId
    ? Math.min(escolhas[produto.produtoId].saldoLote ?? 0, produto.saldoDisponivelConsumo)
    : produto.saldoDisponivelConsumo;
  const ajustar = (produto: EstoqueDTO, delta: number) => {
    const valor = Math.max(0, Math.round(((escolhas[produto.produtoId]?.quantidade ?? 0) + delta) * 1000) / 1000);
    if (valor > limite(produto)) return setErro(`${produto.produtoNome}: quantidade maior que o saldo consumível.`);
    mudar(produto.produtoId, { quantidade: valor });
  };
  const conferir = () => {
    const invalido = selecionados.find(p => escolhas[p.produtoId].quantidade > p.saldoDisponivelConsumo);
    if (invalido) return setErro(`${invalido.produtoNome}: o saldo mudou. Revise a quantidade.`);
    if (selecionados.length) {
      setRevisao(selecionados.map(produto => ({ ...escolhas[produto.produtoId], produto })));
      setErro(""); setConferindo(true);
    }
  };
  const imprimir = () => {
    try { imprimirConsumo(user?.restauranteNome ?? "Restaurante", hojeISO(), revisao.map(item => ({
      produto: item.produto.produtoNome, quantidade: item.quantidade, unidade: item.produto.unidadeMedida,
    })), true); } catch (e) { setErro(mensagemErro(e)); }
  };
  const registrar = async () => {
    if (bloqueio.current) return;
    // A conferência é um retrato imutável. Uma atualização remota não altera o reenvio.
    const itens = revisao.map(item => ({ produtoId: item.produto.produtoId, quantidade: item.quantidade,
      loteId: item.loteId ? Number(item.loteId) : null,
      versionLote: item.versionLote, observacao: item.observacao || null }));
    if (!itens.length) return;
    const conteudo = JSON.stringify(itens);
    if (!tentativa.current || tentativa.current.conteudo !== conteudo) tentativa.current = { conteudo, chave: crypto.randomUUID() };
    bloqueio.current = true; setEnviando(true); setErro("");
    try {
      const resultado = await api.post<ConsumoLoteResultadoDTO>("/api/consumos/lote", { chave: tentativa.current.chave, itens });
      // A confirmação termina aqui. As listas são atualizadas sem prolongar o envio.
      setData(resultado.estoque); setEscolhas({}); tentativa.current = null;
      setIncerto(false); setConferindo(false); onDone(resultado); void refresh();
    } catch (e) {
      const falha = e as ApiError;
      // Sem resposta conclusiva, conservamos exatamente o pedido e sua chave.
      const duvida = !(falha instanceof ApiError) || falha.status === 0 || falha.status >= 500;
      setIncerto(duvida);
      setErro(duvida ? "Não recebemos a confirmação. Toque em Tentar novamente: a mesma operação não será registrada duas vezes." : falha.message);
      if (!duvida) void refresh();
    } finally { bloqueio.current = false; setEnviando(false); }
  };

  return <div className="consumo-rapido">
    {(erro || error) && <p className="alertbanner alertbanner--bad" role="alert">{erro || error}</p>}
    <TextoPorToque id="consumo-busca" label="Buscar produto" placeholder="Buscar produto…" value={busca} onValue={setBusca}
      hint="Toque duas vezes para digitar. Você também pode escolher pela categoria." />
    <div className="consumo-categorias" aria-label="Filtrar por categoria">
      {["", ...categorias].map(nome => <Button key={nome} size="sm" variant={categoria === nome ? "accent" : "outline"}
        aria-pressed={categoria === nome} onClick={() => setCategoria(nome)}>{nome || "Todas"}</Button>)}
      {busca && <Button size="sm" variant="ghost" onClick={() => setBusca("")}>Limpar busca</Button>}
    </div>
    <Checkbox label="Somente selecionados" checked={somenteSelecionados} onChange={setSomenteSelecionados} />
    {loading && !estoque ? <p className="muted">Carregando…</p> : !visiveis.length ? <EmptyState title="Nenhum produto encontrado" text="Troque a categoria ou limpe a busca." /> :
      <div className="consumo-produtos">{visiveis.map(produto => {
        const escolha = escolhas[produto.produtoId] ?? vazio();
        const atalhos = passos(produto.unidadeMedida);
        const passo = produto.unidadeMedida === "G" || produto.unidadeMedida === "ML" ? 10 : atalhos[0];
        const indisponivel = produto.saldoDisponivelConsumo <= 0;
        const selecionado = escolha.quantidade > 0;
        return <section key={produto.produtoId}
          className={`consumo-produto${indisponivel ? "" : " consumo-produto--disponivel"}${selecionado ? " consumo-produto--selecionado" : ""}`}
          aria-label={produto.produtoNome}>
          <div><strong>{produto.produtoNome}</strong><p className="consumo-produto__meta">
            <span className="muted">{produto.categoriaNome}</span>
            <Badge tone={indisponivel ? "bad" : "good"}>
              {indisponivel ? "Sem saldo" : `Disponível: ${fmtNum(produto.saldoDisponivelConsumo)} ${produto.unidadeMedida.toLowerCase()}`}
            </Badge>
          </p></div>
          <div className="consumo-controles">
            <Button variant="outline" disabled={incerto || enviando || escolha.quantidade <= 0} aria-label={`Diminuir ${produto.produtoNome}`} onClick={() => ajustar(produto, -passo)}>−</Button>
            <Button variant={selecionado ? "accent" : "outline"} className="consumo-quantidade" disabled={incerto || enviando || indisponivel} aria-label={`Quantidade de ${produto.produtoNome}`} onClick={() => setPainel(produto)}>{fmtNum(escolha.quantidade)} {produto.unidadeMedida.toLowerCase()}</Button>
            <Button variant="outline" disabled={incerto || enviando || indisponivel} aria-label={`Aumentar ${produto.produtoNome}`} onClick={() => ajustar(produto, passo)}>+</Button>
          </div>
          <div className="consumo-atalhos">{atalhos.map(qtd => <Button key={qtd} size="sm" variant="ghost" disabled={incerto || enviando || indisponivel} onClick={() => ajustar(produto, qtd)}>+{fmtNum(qtd)} {produto.unidadeMedida.toLowerCase()}</Button>)}</div>
          <div className="consumo-detalhes"><Button variant="ghost" size="sm" disabled={incerto || enviando} onClick={() => setDetalhe(produto)}>Lote e observação</Button>
            {escolha.quantidade > 0 && <Button variant="ghost" size="sm" disabled={incerto || enviando} onClick={() => mudar(produto.produtoId, { quantidade: 0 })}>Remover</Button>}</div>
          {indisponivel && <p className="field__hint">Sem saldo consumível.</p>}
        </section>;
      })}</div>}
    <div className="consumo-confirmacao"><span>{selecionados.length} {selecionados.length === 1 ? "produto selecionado" : "produtos selecionados"}</span>
      <Button icon="check" disabled={!selecionados.length || enviando} onClick={conferir}>Registrar consumo de {selecionados.length} {selecionados.length === 1 ? "item" : "itens"}</Button></div>
    {painel && <PainelQuantidade produto={painel.produtoNome} unidade={painel.unidadeMedida}
      quantidade={escolhas[painel.produtoId]?.quantidade ?? 0} limite={limite(painel)}
      onConfirmar={quantidade => mudar(painel.produtoId, { quantidade })} onClose={() => setPainel(null)} />}
    {detalhe && <Modal open title={`Detalhes de ${detalhe.produtoNome}`} onClose={() => setDetalhe(null)} footer={<Button onClick={() => setDetalhe(null)}>Pronto</Button>}>
      <p className="muted">Sem lote manual, o sistema usa primeiro os lotes válidos com vencimento mais próximo.</p>
      <Field label="Lote (opcional)" htmlFor="consumo-lote"><Select id="consumo-lote" disabled={carregandoLotes}
        value={escolhas[detalhe.produtoId]?.loteId ?? ""} onChange={e => { const id = e.target.value; const lote = lotes?.find(l => String(l.id) === id);
          mudar(detalhe.produtoId, { loteId: id, versionLote: lote?.version ?? null, saldoLote: lote?.quantidadeAtual ?? null }); }}>
        <option value="">{carregandoLotes ? "Carregando lotes…" : "Escolha automática"}</option>
        {(lotes ?? []).filter(l => l.disponivel && !l.vencido).map(l => <option key={l.id} value={l.id}>{l.codigo} · saldo {fmtNum(l.quantidadeAtual)} {l.unidadeMedida.toLowerCase()}</option>)}
      </Select></Field>
      <Button variant="ghost" onClick={() => mudar(detalhe.produtoId, { loteId: "", versionLote: null, saldoLote: null })}>Usar lote automático</Button>
      <TextoPorToque id="consumo-observacao" label="Observação (opcional)" maxLength={500} value={escolhas[detalhe.produtoId]?.observacao ?? ""} onValue={observacao => mudar(detalhe.produtoId, { observacao })} />
    </Modal>}
    {conferindo && <Modal open title="Conferir itens do consumo" width="lg" onClose={() => { if (!bloqueio.current && !incerto) setConferindo(false); }} footer={<>
      <Button variant="ghost" disabled={enviando || incerto} onClick={() => setConferindo(false)}>Voltar para editar</Button>
      <Button variant="outline" icon="printer" disabled={enviando} onClick={imprimir}>Imprimir lista</Button>
      <Button icon="check" loading={enviando} onClick={() => void registrar()}>{incerto ? "Tentar novamente" : "Confirmar consumo"}</Button>
    </>}>
      {erro && <p className="alertbanner alertbanner--bad" role="alert">{erro}</p>}
      <p>Confira os itens. O estoque só será alterado ao confirmar.</p>
      <DataTable headers={["Produto", "Quantidade", "Unidade"]}>{revisao.map(item => <tr key={item.produto.produtoId}><td>{item.produto.produtoNome}</td><td>{fmtNum(item.quantidade)}</td><td>{item.produto.unidadeMedida.toLowerCase()}</td></tr>)}</DataTable>
    </Modal>}
  </div>;
}
