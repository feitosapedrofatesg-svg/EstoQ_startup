import { traduzir } from "../lib/i18n";
import { mensagemErro } from "../lib/api";
import { estoquePorUnidade } from "../lib/estoquePorUnidade";
import { useEffect, useState } from "react";
import { useAuth } from "../store/auth";
import { useFetch, useTodasPaginas, useDebounced } from "../lib/hooks";
import { Paginacao } from "../components/Paginacao";
import { api } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  LinkButton,
  Modal,
  Confirm,
  Field,
  Input,
  Select,
  Textarea,
  NumberField,
  SearchSelect,
  DataTable,
  StatusBadge,
  Tabs,
  EmptyState,
} from "../components/UI";
import { fmtNum } from "../lib/format";
import type {
  CategoriaDTO,
  Page,
  ParametroEstoqueDTO,
  ProdutoDTO,
  UnidadeMedida,
} from "../lib/types";
import { useToast } from "../store/toast";

const UNIDADES: UnidadeMedida[] = ["KG", "G", "L", "ML", "UN"];

export function Catalogo() {
  const { user } = useAuth();
  const isAdmin = user?.perfil === "ADMIN";
  const [tab, setTab] = useState("produtos");
  const [search, setSearch] = useState("");
  const [catModal, setCatModal] = useState<{ open: boolean; cat: CategoriaDTO | null }>({
    open: false,
    cat: null,
  });
  const [prodModal, setProdModal] = useState<{ open: boolean; prod: ProdutoDTO | null }>({
    open: false,
    prod: null,
  });
  const [delCat, setDelCat] = useState<CategoriaDTO | null>(null);
  const [delProd, setDelProd] = useState<ProdutoDTO | null>(null);
  const [busy, setBusy] = useState("");
  const toast = useToast();

  const [pagina, setPagina] = useState(0);
  const busca = useDebounced(search.trim());
  useEffect(() => { setPagina(0); }, [busca, tab]);
  const query = `size=50&page=${pagina}&q=${encodeURIComponent(busca)}`;
  const { data: catsPage, refresh: refreshCats, loading: loadingCats, error: errorCats } = useFetch<Page<CategoriaDTO>>(`/api/categorias?${query}&sort=nome,asc`);
  const { data: prodsPage, refresh: refreshProds, loading: loadingProds, error: errorProds } = useFetch<Page<ProdutoDTO>>(`/api/produtos?${query}&sort=id,desc`);
  const { data: todasCats, refresh: refreshTodasCats, error: errorTodasCats } = useTodasPaginas<CategoriaDTO>("/api/categorias?sort=nome,asc");
  const { data: parametro, loading: loadingParam, error: errorParam } = useFetch<ParametroEstoqueDTO>(
    isAdmin && prodModal.prod ? `/api/parametros-estoque/por-produto/${prodModal.prod.id}` : null);
  const cats = catsPage?.content ?? [];
  const prodRows = prodsPage?.content ?? [];
  const loading = tab === "produtos" ? loadingProds : loadingCats;
  const error = tab === "produtos" ? errorProds : errorCats;
  const catById = new Map((todasCats ?? []).map(c => [c.id, c]));
  const paginaAtual = tab === "produtos" ? prodsPage : catsPage;
  useEffect(() => {
    if (paginaAtual && pagina >= paginaAtual.totalPages && pagina > 0) setPagina(Math.max(0, paginaAtual.totalPages - 1));
  }, [paginaAtual, pagina]);

  const doDelete = async (kind: "cat" | "prod", id: number) => {
    setBusy(kind + id);
    try {
      if (kind === "cat") {
        await api.del(`/api/categorias/${id}`);
        setDelCat(null);
        toast.success(traduzir("Categoria removida"));
        void refreshCats();
        void refreshTodasCats();
      } else {
        await api.del(`/api/produtos/${id}`);
        setDelProd(null);
        toast.success(traduzir("Produto removido"));
        void refreshProds();
      }
    } catch (e) {
      toast.error(traduzir("Não foi possível remover"), mensagemErro(e));
    } finally {
      setBusy("");
    }
  };

  return (
    <>
      <PageHeader
        title={traduzir("Catálogo")}
        subtitle={traduzir("Produtos, categorias e parâmetros que alimentam o controle de estoque.")}
        actions={
          isAdmin && (
            <>
              <LinkButton to="/notas-fiscais" variant="outline" icon="arrows">
                {" "}{traduzir("Importar nota fiscal")}{" "}</LinkButton>
              <Button icon="plus" onClick={() => setProdModal({ open: true, prod: null })}>
                {" "}{traduzir("Novo produto")}{" "}</Button>
            </>
          )
        }
      />

      <Card
        title={
          <Tabs
            items={[
              { id: "produtos", label: "Produtos" },
              { id: "categorias", label: "Categorias" },
            ]}
            active={tab}
            onChange={setTab}
          />
        }
        actions={
          <div className="filterbar">
            <Input
              type="search"
              placeholder={traduzir("Buscar no catálogo…")}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label={traduzir("Buscar no catálogo")}
            />
            {isAdmin && (
              <Button
                variant="outline"
                size="sm"
                icon="plus"
                onClick={() => setCatModal({ open: true, cat: null })}
              >
                {" "}{traduzir("Nova categoria")}{" "}</Button>
            )}
          </div>
        }
      >
        {error && <div role="alert" className="alertbanner alertbanner--bad">{traduzir(error)}</div>}
        {loading ? (
          <p className="muted">{" "}{traduzir("Carregando…")}{" "}</p>
        ) : tab === "produtos" ? (
          prodRows.length === 0 ? (
            <EmptyState
              title={traduzir("Nenhum produto")}
              text={traduzir("Cadastre os produtos que sua cozinha usa para começar a controlar o estoque.")}
              action={isAdmin && <Button icon="plus" onClick={() => setProdModal({ open: true, prod: null })}>{" "}{traduzir("Cadastrar produto")}{" "}</Button>}
            />
          ) : (
            <DataTable
              caption={traduzir("Produtos cadastrados")}
              headers={[traduzir("Produto"), traduzir("Categoria"), traduzir("Unidade"), traduzir("Código"), traduzir("Saldo"), traduzir("Situação"), ""]}
            >
              {prodRows.map((p) => (
                <tr key={p.id}>
                  <td>
                    <Button variant="link" onClick={() => setProdModal({ open: true, prod: p })}>
                      {p.nome}
                    </Button>
                  </td>
                  <td className="muted-cell">{p.categoriaNome ?? catById.get(p.categoriaId)?.nome ?? "—"}</td>
                  <td>{p.unidadeMedida === "UN" ? traduzir("Unidade") : p.unidadeMedida}</td>
                  <td className="muted-cell">{p.codigoBarras ?? "—"}</td>
                  <td>{p.saldoAtual ? `${fmtNum(p.saldoAtual)} ${p.unidadeMedida}` : "0"}</td>
                  <td>{p.ativo ? <StatusBadge label={traduzir("Ativo")} tone="good" /> : <StatusBadge label={traduzir("Inativo")} tone="neutral" />}</td>
                  <td>
                    {isAdmin && (
                      <div className="td-actions">
                        <Button size="sm" variant="ghost" icon="edit" onClick={() => setProdModal({ open: true, prod: p })}>
                          {" "}{traduzir("Editar")}{" "}</Button>
                        <Button size="sm" variant="ghost" icon="trash" onClick={() => setDelProd(p)} aria-label={traduzir("Remover {0}",{0:p.nome})} />
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </DataTable>
          )
        ) : (
          (cats ?? []).length === 0 ? (
            <EmptyState title={traduzir("Nenhuma categoria")} text={traduzir("Categorias organizam o catálogo.")} />
          ) : (
            <DataTable caption={traduzir("Categorias")} headers={[traduzir("Categoria"), traduzir("Descrição")]}>
              {(cats ?? []).map((c) => (
                <tr key={c.id}>
                  <td>
                    {c.nome}
                    {isAdmin && (
                      <span className="td-actions" style={{ marginInlineStart: 8 }}>
                        <Button size="sm" variant="ghost" icon="edit" onClick={() => setCatModal({ open: true, cat: c })} aria-label={traduzir("Editar {0}",{0:c.nome})} />
                        <Button size="sm" variant="ghost" icon="trash" onClick={() => setDelCat(c)} aria-label={traduzir("Remover {0}",{0:c.nome})} />
                      </span>
                    )}
                  </td>
                  <td className="muted-cell">{c.descricao ?? "—"}</td>
                </tr>
              ))}
            </DataTable>
          )
        )}
        <Paginacao dados={tab === "produtos" ? prodsPage : catsPage} pagina={pagina} alterar={setPagina} />
      </Card>

      {catModal.open && (
        <CategoriaForm
          cat={catModal.cat}
          onClose={() => setCatModal({ open: false, cat: null })}
          onDone={() => {
            setCatModal({ open: false, cat: null });
            void refreshCats();
            void refreshTodasCats();
          }}
        />
      )}
      {prodModal.open && (loadingParam || errorParam || !todasCats || errorTodasCats || (isAdmin && prodModal.prod && (!parametro || parametro.produtoId !== prodModal.prod.id)) ? <div role="alert">{errorParam ?? errorTodasCats ?? traduzir("Carregando dados do produto…")}</div> : (
        <ProdutoForm
          prod={prodModal.prod}
          cats={todasCats ?? []}
          param={parametro}
          allowParams={isAdmin}
          onClose={() => setProdModal({ open: false, prod: null })}
          onDone={() => {
            setProdModal({ open: false, prod: null });
            void refreshProds();
          }}
        />
      ))}
      <Confirm
        open={!!delCat}
        title={traduzir("Remover categoria")}
        message={traduzir("A categoria \"{0}\" será removida. Produtos nela precisam de outra categoria.",{0:delCat?.nome})}
        danger
        confirmLabel={traduzir("Remover")}
        loading={busy === "cat" + delCat?.id}
        onConfirm={() => delCat && void doDelete("cat", delCat.id)}
        onClose={() => setDelCat(null)}
      />
      <Confirm
        open={!!delProd}
        title={traduzir("Remover produto")}
        message={traduzir("O produto \"{0}\" será desativado. Histórico de movimentações é preservado.",{0:delProd?.nome})}
        danger
        confirmLabel={traduzir("Remover")}
        loading={busy === "prod" + delProd?.id}
        onConfirm={() => delProd && void doDelete("prod", delProd.id)}
        onClose={() => setDelProd(null)}
      />
    </>
  );
}

function CategoriaForm({ cat, onClose, onDone }: { cat: CategoriaDTO | null; onClose: () => void; onDone: () => void }) {
  const [nome, setNome] = useState(cat?.nome ?? "");
  const [descricao, setDescricao] = useState(cat?.descricao ?? "");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const toast = useToast();

  const submit = async () => {
    if (!nome.trim()) return setError(traduzir("Dê um nome à categoria."));
    setSubmitting(true);
    setError(null);
    try {
      const body = { nome: nome.trim(), descricao: descricao.trim() || null };
      if (cat) await api.put(`/api/categorias/${cat.id}`, { ...body, version: cat.version });
      else await api.post("/api/categorias", body);
      toast.success(cat ? "Categoria atualizada" : "Categoria criada");
      onDone();
    } catch (e) {
      setError(mensagemErro(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={cat ? traduzir("Editar categoria") : traduzir("Nova categoria")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>{" "}{traduzir("Cancelar")}{" "}</Button>
          <Button onClick={() => void submit()} loading={submitting}>{" "}{traduzir("Salvar")}{" "}</Button>
        </>
      }
    >
      {error && <div className="alertbanner alertbanner--bad" role="alert">{traduzir(error)}</div>}
      <Field label={traduzir("Nome")} required>
        <Input value={nome} onChange={(e) => setNome(e.target.value)} placeholder={traduzir("Ex.: Hortifruti")} />
      </Field>
      <Field label={traduzir("Descrição (opcional)")}>
        <Textarea value={descricao} onChange={(e) => setDescricao(e.target.value)} placeholder={traduzir("Para que serve essa categoria…")} />
      </Field>
    </Modal>
  );
}

function ProdutoForm({
  prod,
  cats,
  param,
  allowParams,
  onClose,
  onDone,
}: {
  prod: ProdutoDTO | null;
  cats: CategoriaDTO[];
  param: ParametroEstoqueDTO | null;
  allowParams: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const toast = useToast();
  const [nome, setNome] = useState(prod?.nome ?? "");
  const [unidadeEscolhida, setUnidadeMedida] = useState<UnidadeMedida>(prod?.unidadeMedida ?? "KG");
  const [categoriaId, setCategoriaId] = useState(prod ? String(prod.categoriaId) : "");
  const porUnidade = estoquePorUnidade(nome, cats.find(c => String(c.id) === categoriaId)?.nome);
  const unidadeMedida = porUnidade ? "UN" : unidadeEscolhida;
  const [codigoBarras, setCodigoBarras] = useState(prod?.codigoBarras ?? "");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // parâmetros de estoque (admin)
  const [minimo, setMinimo] = useState<number | null>(param?.estoqueMinimo ?? (prod ? null : 3));
  const [medio, setMedio] = useState<number | null>(param?.estoqueMedio ?? (prod ? null : 3));
  const [maximo, setMaximo] = useState<number | null>(param?.estoqueMaximo ?? (prod ? null : 3));
  const [consumo, setConsumo] = useState<number | null>(param?.consumoMedioDiario ?? null);
  const [diasReposicao, setDiasReposicao] = useState<number | null>(param?.tempoReposicaoDias ?? null);
  const [periodo] = useState<number | null>(param?.periodoAnaliseDias ?? null);
  const [diasAlerta, setDiasAlerta] = useState<number | null>(param?.diasAlertaVencimento ?? null);

  const catOpts = cats.map((c) => ({ value: String(c.id), label: c.nome, sub: c.descricao ?? undefined }));

  const submit = async () => {
    if (!nome.trim()) return setError(traduzir("Dê um nome ao produto."));
    if (!categoriaId) return setError(traduzir("Escolha a categoria."));
    setSubmitting(true);
    setError(null);
    try {
      const base = {
        nome: nome.trim(),
        unidadeMedida,
        categoriaId: Number(categoriaId),
        codigoBarras: codigoBarras.trim() || null,
      };
      let savedProd: ProdutoDTO;
      if (prod) {
        savedProd = await api.put<ProdutoDTO>(`/api/produtos/${prod.id}`, { ...base, version: prod.version });
      } else {
        savedProd = await api.post<ProdutoDTO>("/api/produtos", base);
      }

      if (allowParams) {
        const paramBody = {
          produtoId: prod?.id ?? savedProd.id,
          tempoReposicaoDias: diasReposicao ?? 0,
          periodoAnaliseDias: periodo ?? 30,
          consumoMedioDiario: consumo ?? 0,
          estoqueMinimo: minimo ?? 0,
          estoqueMedio: medio ?? 0,
          estoqueMaximo: maximo ?? 0,
          diasAlertaVencimento: diasAlerta ?? 7,
        };
        // Todo produto recebe parâmetros no backend; consultamos o registro correto pelo produto.
        const atual = param ?? await api.get<ParametroEstoqueDTO>(`/api/parametros-estoque/por-produto/${savedProd.id}`);
        await api.put(`/api/parametros-estoque/${atual.id}`, { ...paramBody, version: atual.version });
      }

      toast.success(prod ? "Produto atualizado" : "Produto criado");
      onDone();
    } catch (e) {
      setError(mensagemErro(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={prod ? traduzir("Editar {0}",{0:prod.nome}) : traduzir("Novo produto")}
      width="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>{" "}{traduzir("Cancelar")}{" "}</Button>
          <Button onClick={() => void submit()} loading={submitting}>{" "}{traduzir("Salvar produto")}{" "}</Button>
        </>
      }
    >
      {error && <div className="alertbanner alertbanner--bad" role="alert">{traduzir(error)}</div>}
      <div className="form-grid-2">
        <Field label={traduzir("Nome")} htmlFor="produto-nome" required>
          <Input id="produto-nome" value={nome} onChange={(e) => setNome(e.target.value)} placeholder={traduzir("Ex.: Tomate italiano")} />
        </Field>
        <Field label={traduzir("Unidade de medida")} required>
          <Select value={unidadeMedida} disabled={porUnidade} onChange={(e) => setUnidadeMedida(e.target.value as UnidadeMedida)}>
            {UNIDADES.map((u) => (
              <option key={u} value={u}>{u === "UN" ? traduzir("Unidade (UN)") : u}</option>
            ))}
          </Select>
        </Field>
      </div>
      <SearchSelect label={traduzir("Categoria")} options={catOpts} value={categoriaId} onValue={setCategoriaId} placeholder={traduzir("Escolha a categoria…")} required />
      {porUnidade && <p className="muted">{" "}{traduzir("Bebidas e limpeza são contadas em unidades. Ex.: 6 esponjas = 6 UN. Peso e volume ficam apenas no nome da embalagem.")}{" "}</p>}
      <Field label={traduzir("Código de barras (opcional)")}>
        <Input value={codigoBarras} onChange={(e) => setCodigoBarras(e.target.value)} placeholder={traduzir("Ex.: 7891234567890")} />
      </Field>

      {allowParams && (
        <>
          <h3 className="section-title">{" "}{traduzir("Parâmetros de estoque")}{" "}</h3>
          <p className="muted section-sub">
            {" "}{traduzir("Base para os alertas de reposição e estoque baixo. Novos produtos começam com os níveis em 3 na unidade de cadastro. Use 0 no mínimo para não sinalizar estoque baixo.")}{" "}</p>
          <div className="form-grid-3">
            <NumberField label={traduzir("Estoque mínimo")} htmlFor="produto-estoque-minimo" value={minimo} onValue={setMinimo} min={0} placeholder={traduzir("Ex.: 3")} suffix={unidadeMedida === "UN" ? "UN" : unidadeMedida} />
            <NumberField label={traduzir("Estoque médio")} htmlFor="produto-estoque-medio" value={medio} onValue={setMedio} min={0} placeholder={traduzir("Ex.: 4")} suffix={unidadeMedida === "UN" ? "UN" : unidadeMedida} />
            <NumberField label={traduzir("Estoque máximo")} htmlFor="produto-estoque-maximo" value={maximo} onValue={setMaximo} min={0} placeholder={traduzir("Ex.: 8")} suffix={unidadeMedida === "UN" ? "UN" : unidadeMedida} />
            <NumberField label={traduzir("Consumo diário manual (sem histórico)")} value={consumo} onValue={setConsumo} min={0} placeholder={traduzir("Ex.: 0,4")} suffix={unidadeMedida === "UN" ? "UN/dia" : `${unidadeMedida}/dia`} />
            <NumberField label={traduzir("Tempo de reposição (dias)")} value={diasReposicao} onValue={setDiasReposicao} min={0} placeholder={traduzir("Ex.: 2")} />
            <NumberField label={traduzir("Dias de alerta de validade")} value={diasAlerta} onValue={setDiasAlerta} min={0} placeholder={traduzir("Ex.: 7")} />
          </div>
        </>
      )}
    </Modal>
  );
}
