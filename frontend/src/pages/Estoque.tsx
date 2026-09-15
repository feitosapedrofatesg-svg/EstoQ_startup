import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../store/auth";
import { useFetch } from "../lib/hooks";
import {
  PageHeader,
  Card,
  Input,
  Checkbox,
  DataTable,
  StatusBadge,
  Modal,
  Button,
  EmptyState,
  LinkButton,
} from "../components/UI";
import { fmtMoney, fmtNum, fmtDate, hojeISO } from "../lib/format";
import type { EstoqueDTO, LoteDTO } from "../lib/types";

function saldo(v: number | null | undefined, unidade: string): string {
  if (v === null || v === undefined) return "—";
  return `${fmtNum(v)} ${unidade}`;
}

export function Estoque() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { data, loading, error, refresh } = useFetch<EstoqueDTO[]>("/api/estoque");

  const [q, setQ] = useState("");
  const [soBaixo, setSoBaixo] = useState(false);
  const [soAbertos, setSoAbertos] = useState(false);
  const [sel, setSel] = useState<EstoqueDTO | null>(null);

  const rows = useMemo(() => {
    if (!data) return [];
    const query = q.trim().toLowerCase();
    return data.filter((it) => {
      if (soBaixo && !it.abaixoDoMinimo) return false;
      if (soAbertos && !it.possuiItensAbertos) return false;
      if (!query) return true;
      return (
        it.produtoNome.toLowerCase().includes(query) ||
        it.categoriaNome.toLowerCase().includes(query)
      );
    });
  }, [data, q, soBaixo, soAbertos]);

  return (
    <>
      <PageHeader
        title="Estoque"
        subtitle="Saldo atual por produto, sempre calculado pelos lotes."
        actions={
          user && (user.perfil === "ADMIN" || user.perfil === "COZINHA") ? (
            <LinkButton to="/movimentacoes" icon="plus">
              Registrar movimento
            </LinkButton>
          ) : (
            <Link to="/relatorios" className="btn btn--outline">
              Ver relatórios
            </Link>
          )
        }
      />

      <Card
        title="Produtos e saldos"
        actions={
          <div className="filterbar">
            <label className="search">
              <span className="visually-hidden">Buscar produto</span>
              <Input
                type="search"
                placeholder="Buscar produto…"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                aria-label="Buscar produto por nome ou categoria"
              />
            </label>
            <div className="filterbar__checks">
              <Checkbox checked={soBaixo} onChange={setSoBaixo} label="Só abaixo do mínimo" />
              <Checkbox checked={soAbertos} onChange={setSoAbertos} label="Com embalagem aberta" />
            </div>
            <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
              Atualizar
            </Button>
          </div>
        }
      >
        {error && <div className="alertbanner alertbanner--bad">{error}</div>}
        {loading && !data && <p className="muted">Carregando estoque…</p>}
        {data && data.length === 0 ? (
          <EmptyState
            title="Nenhum produto no estoque ainda"
            text="Cadastre produtos no Catálogo e registre a primeira entrada."
            action={<LinkButton to="/catalogo" icon="plus">Ir para o Catálogo</LinkButton>}
          />
        ) : rows.length === 0 ? (
          <EmptyState
            title="Nada com esses filtros"
            text="Ajuste a busca ou desmarque os filtros para ver mais itens."
            icon="search"
          />
        ) : (
          <DataTable
            caption="Saldo do estoque por produto"
            headers={["Produto", "Categoria", "Saldo", "Valor em estoque", "Mínimo", "Situação"]}
          >
            {rows.map((it) => (
              <tr key={it.produtoId} className="row-click" onClick={() => setSel(it)}>
                <td>
                  <button type="button" className="td-link" onClick={() => setSel(it)}>
                    {it.produtoNome}
                  </button>
                </td>
                <td className="muted-cell">{it.categoriaNome}</td>
                <td>
                  <strong>{saldo(it.saldoAtual, it.unidadeMedida)}</strong>
                </td>
                <td>{fmtMoney(it.valorEstoque)}</td>
                <td className="muted-cell">{saldo(it.estoqueMinimo, it.unidadeMedida)}</td>
                <td>
                  <span className="td-badges">
                    {it.abaixoDoMinimo && <StatusBadge label="Abaixo do mínimo" tone="warn" />}
                    {it.possuiItensAbertos && <StatusBadge label="Embalagem aberta" tone="accent" />}
                    {!it.abaixoDoMinimo && !it.possuiItensAbertos && (
                      <StatusBadge label="Ok" tone="good" />
                    )}
                  </span>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      {sel && (
        <ProdutoDetalhe
          produto={sel}
          onClose={() => setSel(null)}
          onMovimentar={() => {
            const id = sel.produtoId;
            setSel(null);
            navigate(`/movimentacoes?produto=${id}`);
          }}
        />
      )}
    </>
  );
}

function ProdutoDetalhe({
  produto,
  onClose,
  onMovimentar,
}: {
  produto: EstoqueDTO;
  onClose: () => void;
  onMovimentar: () => void;
}) {
  const { data: lotes, loading, refresh } = useFetch<LoteDTO[]>(
    `/api/lotes?produtoId=${produto.produtoId}`
  );
  const { user } = useAuth();

  return (
    <Modal
      open
      onClose={onClose}
      title={
        <>
          {produto.produtoNome}
          <span className="modal-sub">{produto.categoriaNome}</span>
        </>
      }
      width="lg"
      footer={
        user && (user.perfil === "ADMIN" || user.perfil === "COZINHA") ? (
          <Button icon="arrows" onClick={onMovimentar}>
            Movimentar este produto
          </Button>
        ) : (
          <Button variant="ghost" onClick={onClose}>
            Fechar
          </Button>
        )
      }
    >
      <div className="detail-summary">
        <div>
          <span className="detail-summary__label">Saldo disponível</span>
          <strong>{saldo(produto.saldoAtual, produto.unidadeMedida)}</strong>
        </div>
        <div>
          <span className="detail-summary__label">Valor em estoque</span>
          <strong>{fmtMoney(produto.valorEstoque)}</strong>
        </div>
        <div>
          <span className="detail-summary__label">Faixa ideal</span>
          <strong>
            {saldo(produto.estoqueMinimo, produto.unidadeMedida)} –{" "}
            {saldo(produto.estoqueMaximo, produto.unidadeMedida)}
          </strong>
        </div>
      </div>

      <h3 className="section-title">Lotes</h3>
      {loading && <p className="muted">Carregando lotes…</p>}
      {lotes && lotes.length === 0 && (
        <EmptyState title="Sem lotes" text="Registre uma entrada para criar o primeiro lote deste produto." />
      )}
      {lotes && lotes.length > 0 && (
        <DataTable
          caption={`Lotes de ${produto.produtoNome}`}
          headers={["Código", "Entrada", "Validade", "Quantidade", "Preço unit.", "Situação"]}
        >
          {lotes.map((l) => {
            const dias = l.diasParaVencimento;
            const hoje = hojeISO();
            const proximo = l.dataValidade && l.dataValidade <= hoje ? false : dias !== null && dias <= 7;
            return (
              <tr key={l.id}>
                <td>
                  <strong>{l.codigo}</strong>
                </td>
                <td className="muted-cell">{fmtDate(l.dataEntrada)}</td>
                <td className={l.vencido ? "text-bad" : ""}>
                  {fmtDate(l.dataValidade)}
                </td>
                <td>
                  {fmtNum(l.quantidadeAtual)} {l.unidadeMedida}
                </td>
                <td>{fmtMoney(l.precoUnitario)}</td>
                <td>
                  <span className="td-badges">
                    {l.vencido && <StatusBadge label="Vencido" tone="bad" />}
                    {!l.vencido && proximo && <StatusBadge label="Vence logo" tone="warn" />}
                    {!l.vencido && !proximo && l.disponivel && (
                      <StatusBadge label="Disponível" tone="good" />
                    )}
                  </span>
                </td>
              </tr>
            );
          })}
        </DataTable>
      )}
      <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
        Atualizar lotes
      </Button>
    </Modal>
  );
}