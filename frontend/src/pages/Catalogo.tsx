import { useEffect, useState } from "react";
import { useAuth } from "../store/auth";
import { useFetch, useTodasPaginas, useDebounced } from "../lib/hooks";
import { Paginacao } from "../components/Paginacao";
import { api } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
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
        toast.success("Categoria removida");
        void refreshCats();
        void refreshTodasCats();
      } else {
        await api.del(`/api/produtos/${id}`);
        setDelProd(null);
        toast.success("Produto removido");
        void refreshProds();
      }
    } catch (e) {
      toast.error("Não foi possível remover", (e as Error).message);
    } finally {
      setBusy("");
    }
  };

  return (
    <>
      <PageHeader
        title="Catálogo"
        subtitle="Produtos, categorias e parâmetros que alimentam o controle de estoque."
        actions={
          isAdmin && (
            <Button icon="plus" onClick={() => setProdModal({ open: true, prod: null })}>
              Novo produto
            </Button>
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
              placeholder="Buscar no catálogo…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Buscar no catálogo"
            />
            {isAdmin && (
              <Button
                variant="outline"
                size="sm"
                icon="plus"
                onClick={() => setCatModal({ open: true, cat: null })}
              >
                Nova categoria
              </Button>
            )}
          </div>
        }
      >
        {error && <div role="alert" className="alertbanner alertbanner--bad">{error}</div>}
        {loading ? (
          <p className="muted">Carregando…</p>
        ) : tab === "produtos" ? (
          prodRows.length === 0 ? (
            <EmptyState
              title="Nenhum produto"
              text="Cadastre os produtos que sua cozinha usa para começar a controlar o estoque."
              action={isAdmin && <Button icon="plus" onClick={() => setProdModal({ open: true, prod: null })}>Cadastrar produto</Button>}
            />
          ) : (
            <DataTable
              caption="Produtos cadastrados"
              headers={["Produto", "Categoria", "Unidade", "Código", "Saldo", "Situação", ""]}
            >
              {prodRows.map((p) => (
                <tr key={p.id}>
                  <td>
                    <Button variant="link" onClick={() => setProdModal({ open: true, prod: p })}>
                      {p.nome}
                    </Button>
                  </td>
                  <td className="muted-cell">{p.categoriaNome ?? catById.get(p.categoriaId)?.nome ?? "—"}</td>
                  <td>{p.unidadeMedida === "UN" ? "Unidade" : p.unidadeMedida}</td>
                  <td className="muted-cell">{p.codigoBarras ?? "—"}</td>
                  <td>{p.saldoAtual ? `${fmtNum(p.saldoAtual)} ${p.unidadeMedida}` : "0"}</td>
                  <td>{p.ativo ? <StatusBadge label="Ativo" tone="good" /> : <StatusBadge label="Inativo" tone="neutral" />}</td>
                  <td>
                    {isAdmin && (
                      <div className="td-actions">
                        <Button size="sm" variant="ghost" icon="edit" onClick={() => setProdModal({ open: true, prod: p })}>
                          Editar
                        </Button>
                        <Button size="sm" variant="ghost" icon="trash" onClick={() => setDelProd(p)} aria-label={`Remover ${p.nome}`} />
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </DataTable>
          )
        ) : (
          (cats ?? []).length === 0 ? (
            <EmptyState title="Nenhuma categoria" text="Categorias organizam o catálogo." />
          ) : (
            <DataTable caption="Categorias" headers={["Categoria", "Descrição"]}>
              {(cats ?? []).map((c) => (
                <tr key={c.id}>
                  <td>
                    {c.nome}
                    {isAdmin && (
                      <span className="td-actions" style={{ marginInlineStart: 8 }}>
                        <Button size="sm" variant="ghost" icon="edit" onClick={() => setCatModal({ open: true, cat: c })} aria-label={`Editar ${c.nome}`} />
                        <Button size="sm" variant="ghost" icon="trash" onClick={() => setDelCat(c)} aria-label={`Remover ${c.nome}`} />
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
      {prodModal.open && (loadingParam || errorParam || !todasCats || errorTodasCats || (isAdmin && prodModal.prod && (!parametro || parametro.produtoId !== prodModal.prod.id)) ? <div role="alert">{errorParam ?? errorTodasCats ?? "Carregando dados do produto…"}</div> : (
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
        title="Remover categoria"
        message={`A categoria "${delCat?.nome}" será removida. Produtos nela precisam de outra categoria.`}
        danger
        confirmLabel="Remover"
        loading={busy === "cat" + delCat?.id}
        onConfirm={() => delCat && void doDelete("cat", delCat.id)}
        onClose={() => setDelCat(null)}
      />
      <Confirm
        open={!!delProd}
        title="Remover produto"
        message={`O produto "${delProd?.nome}" será desativado. Histórico de movimentações é preservado.`}
        danger
        confirmLabel="Remover"
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
    if (!nome.trim()) return setError("Dê um nome à categoria.");
    setSubmitting(true);
    setError(null);
    try {
      const body = { nome: nome.trim(), descricao: descricao.trim() || null };
      if (cat) await api.put(`/api/categorias/${cat.id}`, { ...body, version: cat.version });
      else await api.post("/api/categorias", body);
      toast.success(cat ? "Categoria atualizada" : "Categoria criada");
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
      title={cat ? `Editar categoria` : "Nova categoria"}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>Cancelar</Button>
          <Button onClick={() => void submit()} loading={submitting}>Salvar</Button>
        </>
      }
    >
      {error && <div className="alertbanner alertbanner--bad" role="alert">{error}</div>}
      <Field label="Nome" required>
        <Input value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Ex.: Hortifruti" />
      </Field>
      <Field label="Descrição (opcional)">
        <Textarea value={descricao} onChange={(e) => setDescricao(e.target.value)} placeholder="Para que serve essa categoria…" />
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
  const [unidadeMedida, setUnidadeMedida] = useState<UnidadeMedida>(prod?.unidadeMedida ?? "KG");
  const [categoriaId, setCategoriaId] = useState(prod ? String(prod.categoriaId) : "");
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
    if (!nome.trim()) return setError("Dê um nome ao produto.");
    if (!categoriaId) return setError("Escolha a categoria.");
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
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={prod ? `Editar ${prod.nome}` : "Novo produto"}
      width="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>Cancelar</Button>
          <Button onClick={() => void submit()} loading={submitting}>Salvar produto</Button>
        </>
      }
    >
      {error && <div className="alertbanner alertbanner--bad" role="alert">{error}</div>}
      <div className="form-grid-2">
        <Field label="Nome" htmlFor="produto-nome" required>
          <Input id="produto-nome" value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Ex.: Tomate italiano" />
        </Field>
        <Field label="Unidade de medida" required>
          <Select value={unidadeMedida} onChange={(e) => setUnidadeMedida(e.target.value as UnidadeMedida)}>
            {UNIDADES.map((u) => (
              <option key={u} value={u}>{u === "UN" ? "Unidade (UN)" : u}</option>
            ))}
          </Select>
        </Field>
      </div>
      <SearchSelect label="Categoria" options={catOpts} value={categoriaId} onValue={setCategoriaId} placeholder="Escolha a categoria…" required />
      <Field label="Código de barras (opcional)">
        <Input value={codigoBarras} onChange={(e) => setCodigoBarras(e.target.value)} placeholder="Ex.: 7891234567890" />
      </Field>

      {allowParams && (
        <>
          <h3 className="section-title">Parâmetros de estoque</h3>
          <p className="muted section-sub">
            Base para os alertas de reposição e estoque baixo. Novos produtos começam com os níveis em 3 na unidade de cadastro. Use 0 no mínimo para não sinalizar estoque baixo.
          </p>
          <div className="form-grid-3">
            <NumberField label="Estoque mínimo" htmlFor="produto-estoque-minimo" value={minimo} onValue={setMinimo} min={0} placeholder="Ex.: 3" suffix={unidadeMedida === "UN" ? "UN" : unidadeMedida} />
            <NumberField label="Estoque médio" htmlFor="produto-estoque-medio" value={medio} onValue={setMedio} min={0} placeholder="Ex.: 4" suffix={unidadeMedida === "UN" ? "UN" : unidadeMedida} />
            <NumberField label="Estoque máximo" htmlFor="produto-estoque-maximo" value={maximo} onValue={setMaximo} min={0} placeholder="Ex.: 8" suffix={unidadeMedida === "UN" ? "UN" : unidadeMedida} />
            <NumberField label="Consumo médio diário" value={consumo} onValue={setConsumo} min={0} placeholder="Ex.: 0,4" suffix={unidadeMedida === "UN" ? "UN/dia" : `${unidadeMedida}/dia`} />
            <NumberField label="Tempo de reposição (dias)" value={diasReposicao} onValue={setDiasReposicao} min={0} placeholder="Ex.: 2" />
            <NumberField label="Dias de alerta de validade" value={diasAlerta} onValue={setDiasAlerta} min={0} placeholder="Ex.: 7" />
          </div>
        </>
      )}
    </Modal>
  );
}
