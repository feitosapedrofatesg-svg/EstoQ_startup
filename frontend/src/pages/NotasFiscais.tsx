import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import { useFetch, invalidarCache } from "../lib/hooks";
import { fmtDate, fmtMoney, fmtNum, parseDecimal } from "../lib/format";
import type { CategoriaDTO, ItemNotaFiscalDTO, NotaFiscalDTO, Page, ProdutoDTO, UnidadeMedida } from "../lib/types";
import { PageHeader, Card, Button, LinkButton, Field, Input, Select, Checkbox, SearchSelect, AlertBanner } from "../components/UI";
import { useToast } from "../store/toast";

function CampoDecimal({ id, label, value, onValue }: { id: string; label: string; value: number; onValue: (valor: number) => void }) {
  const [texto, setTexto] = useState(String(value).replace(".", ","));
  useEffect(() => { setTexto(String(value).replace(".", ",")); }, [value]);
  return <Field label={label} htmlFor={id}><Input id={id} inputMode="decimal" value={texto}
    onChange={e => setTexto(e.target.value)} onBlur={() => { const novoValor = parseDecimal(texto) ?? NaN; if (!Object.is(novoValor, value)) onValue(novoValor); }} /></Field>;
}
const UNIDADES: UnidadeMedida[] = ["KG", "G", "L", "ML", "UN"];

export function NotasFiscais() {
  const [params, setParams] = useSearchParams();
  const inicioLista = useRef<HTMLDivElement>(null);
  const finalLista = useRef<HTMLDivElement>(null);
  const navegarLista = (destino: HTMLDivElement | null) => {
    if (!destino) return;
    // Mantém a navegação acessível e respeita a preferência por movimento reduzido.
    const reduzirMovimento = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    destino.scrollIntoView({ behavior: reduzirMovimento ? "instant" : "smooth", block: "start" });
    destino.focus({ preventScroll: true });
  };
  const [nota, setNota] = useState<NotaFiscalDTO | null>(null);
  const [itens, setItens] = useState<ItemNotaFiscalDTO[]>([]);
  const [arquivos, setArquivos] = useState<File[]>([]);
  const [previews, setPreviews] = useState<{ nome: string; url: string; indice: number }[]>([]);
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [custosConferidos, setCustosConferidos] = useState(false);
  const [produtos, setProdutos] = useState<ProdutoDTO[]>([]);
  const { data: categorias, refresh: atualizarCategorias } = useFetch<Page<CategoriaDTO>>("/api/categorias?size=2000");
  const toast = useToast();
  const notaId = params.get("id");
  useEffect(() => {
    const imagens = arquivos.flatMap((arquivo, indice) => /\.(jpe?g|png)$/i.test(arquivo.name)
      ? [{ nome: arquivo.name, indice, url: URL.createObjectURL(arquivo) }] : []);
    setPreviews(imagens);
    return () => imagens.forEach(imagem => URL.revokeObjectURL(imagem.url));
  }, [arquivos]);
  const selecionarArquivos = (novos: File[]) => {
    setArquivos(novos); setNota(null); setItens([]); setCustosConferidos(false);
    setErro(null); setParams({}, { replace: true });
  };
  const ordenar = (indice: number, deslocamento: number) => {
    const copia = [...arquivos]; const destino = indice + deslocamento;
    [copia[indice], copia[destino]] = [copia[destino], copia[indice]];
    selecionarArquivos(copia);
  };

  const carregarCatalogo = async () => {
    const lista: ProdutoDTO[] = [];
    let pagina = 0, totalPaginas = 1;
    do {
      const resultado = await api.get<Page<ProdutoDTO>>(`/api/produtos?size=100&page=${pagina}`);
      lista.push(...resultado.content); totalPaginas = resultado.totalPages; pagina++;
    } while (pagina < totalPaginas);
    setProdutos(lista);
  };
  const mostrar = (resultado: NotaFiscalDTO) => {
    setNota(resultado);
    setItens(resultado.itens);
    setCustosConferidos(false);
  };
  useEffect(() => {
    void carregarCatalogo().catch(e => setErro((e as Error).message));
  }, []);
  useEffect(() => {
    let ativo = true;
    if (notaId && nota?.id !== Number(notaId)) {
      void api.get<NotaFiscalDTO>(`/api/notas-fiscais/${notaId}`)
        .then(resultado => { if (ativo) mostrar(resultado); })
        .catch(e => { if (ativo) setErro((e as Error).message); });
    }
    return () => { ativo = false; };
  }, [notaId]);

  const alterar = (id: number, campos: Partial<ItemNotaFiscalDTO>) => {
    setItens(anteriores => anteriores.map(i => i.id === id ? { ...i, ...campos } : i));
    setCustosConferidos(false);
  };
  const analisar = async () => {
    setErro(null);
    if (!arquivos.length || arquivos.length > 10 || arquivos.reduce((s, a) => s + a.size, 0) > 4 * 1024 * 1024) {
      setErro("Selecione um documento ou até dez imagens, somando no máximo 4 MB."); return;
    }
    setOcupado(true);
    try {
      const corpo = new FormData(); arquivos.forEach(a => corpo.append("arquivos", a));
      const resultado = await api.post<NotaFiscalDTO>("/api/notas-fiscais/analisar", corpo);
      mostrar(resultado); setParams({ id: String(resultado.id) }, { replace: true });
    } catch (e) { setErro((e as Error).message); }
    finally { setOcupado(false); }
  };
  const confirmar = async () => {
    if (!nota) return;
    if (pendencias.length) {
      setErro("Ajuste as pendências indicadas abaixo antes de confirmar a entrada.");
      return;
    }
    setErro(null); setOcupado(true);
    try {
      const resultado = await api.post<NotaFiscalDTO>(`/api/notas-fiscais/${nota.id}/confirmar`, {
        version: nota.version, custosConferidos,
        itens: itens.map(({ id, selecionado, produtoId, nomeProduto, categoriaId, unidadeEstoque, quantidadeNota,
          fator, valorEntrada, validade }) => ({ id, selecionado, produtoId,
          nomeProduto, categoriaId, unidadeEstoque, quantidadeNota, fator, valorEntrada, validade,
          embalagemConfirmada: custosConferidos, leituraConferida: custosConferidos })),
      });
      mostrar(resultado);
      invalidarCache(["/api/categorias", "/api/produtos", "/api/estoque", "/api/lotes", "/api/movimentacoes", "/api/dashboard", "/api/alertas"]);
      void carregarCatalogo().catch(e => setErro((e as Error).message));
      void atualizarCategorias();
      toast.success("Entrada confirmada", "Produtos, lotes e estoque foram atualizados.");
    } catch (e) { setErro((e as Error).message); }
    finally { setOcupado(false); }
  };
  const selecionados = itens.filter(i => i.selecionado);
  const totalSelecionado = selecionados.reduce((s, i) => s + (i.valorEntrada ?? 0), 0);
  const totalRevisado = itens.reduce((s, i) => s + (i.selecionado ? i.valorEntrada ?? 0 : nota?.itens.find(o => o.id === i.id)?.valorEntrada ?? 0), 0);
  const totalFecha = !!nota && Math.abs(totalRevisado - nota.valorTotal) < 0.005;
  // O botão não esconde a causa do bloqueio: cada pendência aparece junto da confirmação.
  const pendencias: string[] = [];
  if (!selecionados.length) pendencias.push("Selecione pelo menos um item para entrar no estoque.");
  if (!totalFecha) pendencias.push("A soma dos custos precisa fechar o total da nota.");
  for (const item of selecionados) {
    const linha = `Item ${item.numero} (${item.descricao})`;
    if (!item.produtoId && !item.nomeProduto?.trim()) pendencias.push(`${linha}: informe o nome do produto.`);
    if (!Number.isFinite(item.quantidadeNota * item.fator) || item.quantidadeNota <= 0 || item.fator <= 0)
      pendencias.push(`${linha}: informe quantidade e conteúdo da embalagem maiores que zero.`);
    if (!Number.isFinite(item.valorEntrada) || item.valorEntrada < 0)
      pendencias.push(`${linha}: informe um custo válido, igual ou maior que zero.`);
  }

  return <>
    <PageHeader title="Importar nota fiscal" subtitle="Leia a nota, confira os itens e confirme a entrada no estoque."
      actions={<LinkButton to="/movimentacoes" variant="outline">Voltar às movimentações</LinkButton>} />
    {erro && <AlertBanner tone="bad">{erro}</AlertBanner>}
    <Card title="Documento da compra">
      <Field label="Nota fiscal em PDF, XML, JPG ou PNG" htmlFor="nota-arquivos">
        <Input id="nota-arquivos" type="file" multiple accept=".pdf,.xml,.jpg,.jpeg,.png,application/pdf,application/xml,text/xml,image/jpeg,image/png"
          disabled={ocupado} onChange={e => { selecionarArquivos(Array.from(e.target.files ?? [])); }} />
      </Field>
      <Field label="Fotografar a nota pelo celular" htmlFor="nota-camera">
        <Input id="nota-camera" type="file" accept="image/jpeg,image/png" capture="environment" disabled={ocupado}
          onChange={e => { const foto = e.target.files?.[0]; if (foto) selecionarArquivos([...arquivos.filter(a => /\.(jpe?g|png)$/i.test(a.name)), foto]); e.target.value = ""; }} />
      </Field>
      <p className="muted">Um PDF/XML ou até dez fotos da mesma nota, na ordem das páginas. Até 4 MB no total. O estoque só muda após a confirmação.</p>
      {previews.length > 0 && <div className="nota-fotos">
        {previews.map(imagem => <figure key={imagem.url}>
          <a href={imagem.url} target="_blank" rel="noreferrer" aria-label={`Abrir imagem da página ${imagem.indice + 1}`}>
            <img src={imagem.url} alt={`Página ${imagem.indice + 1} da nota fiscal`} />
          </a>
          <figcaption>Página {imagem.indice + 1} · {imagem.nome}</figcaption>
          <div className="form-actions">
            <Button size="sm" variant="outline" disabled={ocupado || imagem.indice === 0} aria-label={`Mover página ${imagem.indice + 1} para antes`} onClick={() => ordenar(imagem.indice, -1)}>Antes</Button>
            <Button size="sm" variant="outline" disabled={ocupado || imagem.indice === arquivos.length - 1} aria-label={`Mover página ${imagem.indice + 1} para depois`} onClick={() => ordenar(imagem.indice, 1)}>Depois</Button>
            <Button size="sm" variant="danger" disabled={ocupado} aria-label={`Remover página ${imagem.indice + 1}`} onClick={() => selecionarArquivos(arquivos.filter((_, indice) => indice !== imagem.indice))}>Remover</Button>
          </div>
        </figure>)}
      </div>}
      {previews.length > 0 && <p className="muted">Clique na imagem para conferir em outra aba enquanto revisa os itens.</p>}
      <Button loading={ocupado} disabled={!arquivos.length} onClick={() => void analisar()}>Ler nota fiscal</Button>
    </Card>
    {nota && <>
      <Card title={`NF-e ${nota.numero} · ${nota.fornecedor}`}>
        {!nota.confirmada && <p className="muted">Categorias sugeridas pelo catálogo e pelo nome do item. Sem correspondência, o produto entra em “Sem classificação”. Você pode alterar as sugestões abaixo.</p>}
        <p>Emissão: {fmtDate(nota.emissao)} · Total: <strong>{fmtMoney(nota.valorTotal)}</strong></p>
        <p className="muted" style={{ overflowWrap: "anywhere" }}>Chave: {nota.chave}</p>
        {nota.avisos.map((aviso, index) => <AlertBanner key={index} tone="warn">{aviso}</AlertBanner>)}
        {nota.confirmada ? <AlertBanner tone="good">Esta nota já foi importada. Enviá-la novamente não duplica o estoque.</AlertBanner>
          : <><p>{selecionados.length} de {itens.length} itens selecionados · Entrada: <strong>{fmtMoney(totalSelecionado)}</strong></p>
          <Field label="Categoria para todos os produtos novos" htmlFor="nota-categoria-geral">
            <Select id="nota-categoria-geral" defaultValue="" onChange={e => {
              const categoriaId = Number(e.target.value) || null;
              setItens(prev => prev.map(i => !i.produtoId ? { ...i, categoriaId } : i)); setCustosConferidos(false);
            }}><option value="">Usar classificação automática</option>{categorias?.content.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}</Select>
          </Field></>}
      </Card>
      <nav className="nota-navegacao" aria-label="Navegação dos itens da nota fiscal">
        <Button variant="outline" onClick={() => navegarLista(inicioLista.current)}>↑ Voltar ao início</Button>
        <Button onClick={() => navegarLista(finalLista.current)}>↓ Ir ao final da lista</Button>
      </nav>
      <div ref={inicioLista} className="nota-lista-destino" tabIndex={-1} aria-label="Início da lista de itens" />
      {itens.map(item => <Card key={`${nota.id}-${item.id}`} className={item.incerto && !nota.confirmada ? "nota-item--ocr" : ""} title={`${item.numero}. ${item.descricao}`}>
        <p className="muted">Código {item.codigo} · Nota: {fmtNum(item.quantidadeNota)} {item.unidadeNota} · Bruto: {fmtMoney(item.valorBruto)} · Desconto: {fmtMoney(item.desconto)} · Ajuste rateado: {fmtMoney(item.valorRateado)}</p>
        {!nota.confirmada && <Checkbox checked={item.selecionado} onChange={selecionado => alterar(item.id, { selecionado })} label="Incluir este item no estoque" />}
        {item.selecionado && <fieldset disabled={ocupado || nota.confirmada} style={{ border: 0, padding: 0, minWidth: 0 }}>
          <SearchSelect label="Produto do catálogo" options={[{ value: "novo", label: "Criar produto novo" }, ...produtos.map(p => ({ value: String(p.id), label: p.nome, sub: `${p.categoriaNome} · ${p.unidadeMedida}` }))]}
            value={item.produtoId ? String(item.produtoId) : "novo"} onValue={valor => {
              const produto = produtos.find(p => String(p.id) === valor);
              alterar(item.id, { produtoId: produto?.id ?? null, nomeProduto: produto?.nome ?? item.descricao.slice(0, 120),
                categoriaId: produto?.categoriaId ?? null, unidadeEstoque: produto?.unidadeMedida ?? item.unidadeEstoque,
                embalagemConfirmada: false });
            }} />
          {!item.produtoId && <div className="form-grid-2">
            <Field label="Nome do novo produto" htmlFor={`nota-nome-${item.id}`}><Input id={`nota-nome-${item.id}`} value={item.nomeProduto} maxLength={120} onChange={e => alterar(item.id, { nomeProduto: e.target.value })} /></Field>
            <Field label="Categoria" htmlFor={`nota-categoria-${item.id}`}><Select id={`nota-categoria-${item.id}`} value={item.categoriaId ?? ""} onChange={e => alterar(item.id, { categoriaId: Number(e.target.value) || null })}>
              <option value="">Automática · Sem classificação se não reconhecido</option>{categorias?.content.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
            </Select></Field>
          </div>}
          <div className="form-grid-2">
            <CampoDecimal label="Quantidade na nota" id={`nota-qtd-${item.id}`} value={item.quantidadeNota} onValue={quantidadeNota => alterar(item.id, { quantidadeNota })} />
            <Field label="Unidade no estoque" htmlFor={`nota-unidade-${item.id}`}><Select id={`nota-unidade-${item.id}`} value={item.unidadeEstoque} disabled={!!item.produtoId} onChange={e => alterar(item.id, { unidadeEstoque: e.target.value as UnidadeMedida, embalagemConfirmada: false })}>
              {UNIDADES.map(u => <option key={u} value={u}>{u}</option>)}
            </Select></Field>
            <CampoDecimal label="Conteúdo de cada unidade da nota" id={`nota-fator-${item.id}`} value={item.fator} onValue={fator => alterar(item.id, { fator, embalagemConfirmada: false })} />
            <CampoDecimal label="Custo total deste item (R$)" id={`nota-custo-${item.id}`} value={item.valorEntrada} onValue={valorEntrada => alterar(item.id, { valorEntrada })} />
            <Field label="Validade (opcional)" htmlFor={`nota-validade-${item.id}`}><Input id={`nota-validade-${item.id}`} type="date" value={item.validade ?? ""} onChange={e => alterar(item.id, { validade: e.target.value || null })} /></Field>
          </div>
          <p><strong>Entrada: {fmtNum(item.quantidadeNota * item.fator)} {item.unidadeEstoque}</strong> ({fmtNum(item.quantidadeNota)} × {fmtNum(item.fator)})</p>
        </fieldset>}
      </Card>)}
      <div ref={finalLista} className="nota-lista-destino" tabIndex={-1} aria-label="Final da lista de itens" />
      {!nota.confirmada && <Card title="Confirmar entrada">
        <p>Custo dos itens selecionados: <strong>{fmtMoney(totalSelecionado)}</strong></p>
        <p>Conferência da nota inteira: {fmtMoney(totalRevisado)} de {fmtMoney(nota.valorTotal)}</p>
        {!totalFecha && <AlertBanner tone="warn">Os custos precisam fechar o total da nota, considerando também as linhas desmarcadas.</AlertBanner>}
        <p className="muted">A conferência abaixo vale para todos os itens, incluindo os dados da imagem, as unidades e o conteúdo das embalagens.</p>
        <Checkbox checked={custosConferidos} onChange={setCustosConferidos} label="Conferi os itens, os descontos e os custos rateados" />
        {custosConferidos && pendencias.length > 0 && <AlertBanner tone="warn">
          <strong>Pendências para confirmar a entrada:</strong>
          <ul>{pendencias.map((pendencia, indice) => <li key={indice}>{pendencia}</li>)}</ul>
        </AlertBanner>}
        {erro && <AlertBanner tone="bad">{erro}</AlertBanner>}
        <Button loading={ocupado} disabled={!custosConferidos} onClick={() => void confirmar()}>Confirmar entrada no estoque</Button>
      </Card>}
    </>}
  </>;
}
