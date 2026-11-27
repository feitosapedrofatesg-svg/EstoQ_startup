import { traduzir } from "../lib/i18n";
import { api, downloadBlob } from "../lib/api";
import { Paginacao } from "../components/Paginacao";
import { idiomaAtual } from "../lib/i18n";
import { fmtLote } from "../lib/format";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../store/auth";
import { useFetch } from "../lib/hooks";
import {
  PageHeader,
  Card,
  Input,
  Select,
  Checkbox,
  DataTable,
  StatusBadge,
  Modal,
  Button,
  EmptyState,
  LinkButton,
} from "../components/UI";
import { fmtMoney, fmtNum, fmtDate } from "../lib/format";
import type { EstoqueDTO, LoteDTO } from "../lib/types";

function saldo(v: number | null | undefined, unidade: string): string {
  if (v === null || v === undefined) return "—";
  return `${fmtNum(v)} ${unidade}`;
}

export function Estoque() {
  const { user } = useAuth();
  const navigate = useNavigate();


  const [q, setQ] = useState("");
  const [soBaixo, setSoBaixo] = useState(false);
  const [soAbertos, setSoAbertos] = useState(false);
  const [sel, setSel] = useState<EstoqueDTO | null>(null);

  const [pagina,setPagina]=useState(0);
  const [tamanho,setTamanho]=useState(25);
  const [situacao,setSituacao]=useState("TODOS");
  const qs=new URLSearchParams({q,somenteBaixo:String(soBaixo),somenteAbertos:String(soAbertos),situacao});
  const {data,loading,error,refresh}=useFetch<import("../lib/types").Page<EstoqueDTO> & {valorTotal:number}>(`/api/estoque/pagina?${qs}&page=${pagina}&size=${tamanho}`);
  const rows=data?.content ?? [];
  useEffect(()=>setPagina(0),[q,soBaixo,soAbertos,situacao,tamanho]);
  useEffect(()=>{if(data && pagina>0 && pagina>=data.totalPages)setPagina(Math.max(0,data.totalPages-1));},[data,pagina]);
  const exportar=async()=>{try {const arquivo=await api.blob(`/api/estoque/planilha?${qs}&idioma=${idiomaAtual()}`);downloadBlob(arquivo,"estoQ-estoque.xlsx");}catch(e){window.alert(e instanceof Error?e.message:"Não foi possível exportar.");}};

  return (
    <>
      <PageHeader
        title={traduzir("Estoque")}
        subtitle={traduzir("Saldo atual por produto, sempre calculado pelos lotes.")}
        actions={
          user && (user.perfil === "ADMIN" || user.perfil === "COZINHA" || user.perfil === "OPERACIONAL") ? (
            <>
              <LinkButton to="/consumo" icon="check-circle" className="btn--raised">
                {" "}{traduzir("Registrar consumo")}{" "}</LinkButton>
              <LinkButton to="/desperdicio" icon="trash" className="btn--raised">
                {" "}{traduzir("Registrar desperdício")}{" "}</LinkButton>
            </>
          ) : (
            <Link to="/relatorios" className="btn btn--outline">
              {" "}{traduzir("Ver relatórios")}{" "}</Link>
          )
        }
      />

      <Card
        title={traduzir("Produtos e saldos")}
        actions={
          <div className="filterbar">
            <label className="search">
              <span className="visually-hidden">{" "}{traduzir("Buscar produto")}{" "}</span>
              <Input
                type="search"
                placeholder={traduzir("Buscar produto…")}
                value={q}
                onChange={(e) => setQ(e.target.value)}
                aria-label={traduzir("Buscar produto por nome ou categoria")}
              />
            </label>
            <Select aria-label={traduzir("Situação do estoque")} value={situacao} onChange={e=>setSituacao(e.target.value)}><option value="TODOS">{" "}{traduzir("Todas as situações")}{" "}</option><option value="VENCIDO">{" "}{traduzir("Vencido")}{" "}</option><option value="PROXIMO">{" "}{traduzir("Próximo do vencimento")}{" "}</option></Select>
            <div className="filterbar__checks">
              <Checkbox checked={soBaixo} onChange={setSoBaixo} label={traduzir("Só abaixo do mínimo")} />
              <Checkbox checked={soAbertos} onChange={setSoAbertos} label={traduzir("Com embalagem aberta")} />
            </div>
            <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
              {" "}{traduzir("Atualizar")}{" "}</Button>
          </div>
        }
      >
        {user?.perfil === "ADMIN" && <Button variant="outline" icon="download" onClick={()=>void exportar()}>{" "}{traduzir("Planilha")}{" "}</Button>}
        {data && <p className="muted">{data.totalElements}{" "}{traduzir("registros · Valor em estoque:")}{" "}{fmtMoney(data.valorTotal)}</p>}
        {error && <div className="alertbanner alertbanner--bad">{traduzir(error)}</div>}
        {loading && !data ? (
          <p className="muted">{" "}{traduzir("Carregando estoque…")}{" "}</p>
        ) : data && data.totalElements === 0 ? (
          <EmptyState
            title={traduzir("Nenhum produto no estoque ainda")}
            text={traduzir("Cadastre produtos no Catálogo e registre a primeira entrada.")}
            action={<LinkButton to="/catalogo" icon="plus">{" "}{traduzir("Ir para o Catálogo")}{" "}</LinkButton>}
          />
        ) : rows.length === 0 ? (
          <EmptyState
            title={traduzir("Nada com esses filtros")}
            text={traduzir("Ajuste a busca ou desmarque os filtros para ver mais itens.")}
            icon="search"
          />
        ) : (
          <DataTable
            caption={traduzir("Saldo do estoque por produto")}
            headers={[traduzir("Produto"), traduzir("Categoria"), traduzir("Saldo"), traduzir("Valor em estoque"), traduzir("Mínimo"), traduzir("Situação")]}
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
                    {it.possuiLotesVencidos && <StatusBadge label={traduzir("Vencido")} tone="bad" />}
                    {it.proximoVencimento && <StatusBadge label={traduzir("Próximo do vencimento")} tone="warn" />}
                    {it.abaixoDoMinimo && <StatusBadge label={traduzir("Abaixo do mínimo")} tone="warn" />}
                    {it.possuiItensAbertos && <StatusBadge label={traduzir("Embalagem aberta")} tone="accent" />}
                    {!it.abaixoDoMinimo && !it.possuiItensAbertos && !it.possuiLotesVencidos && !it.proximoVencimento && (
                      <StatusBadge label={traduzir("Ok")} tone="good" />
                    )}
                  </span>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
        <Paginacao dados={data} pagina={pagina} alterar={setPagina} itensPorPagina={tamanho} alterarTamanho={setTamanho} />
      </Card>

      {sel && (
        <ProdutoDetalhe
          produto={sel}
          onClose={() => setSel(null)}
          onRegistrarConsumo={() => {
            const id = sel.produtoId;
            setSel(null);
            navigate(`/consumo?produto=${id}`);
          }}
        />
      )}
    </>
  );
}

function ProdutoDetalhe({
  produto,
  onClose,
  onRegistrarConsumo,
}: {
  produto: EstoqueDTO;
  onClose: () => void;
  onRegistrarConsumo: () => void;
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
        user && (user.perfil === "ADMIN" || user.perfil === "COZINHA" || user.perfil === "OPERACIONAL") ? (
          <Button icon="check-circle" onClick={onRegistrarConsumo}>
            {" "}{traduzir("Registrar consumo")}{" "}</Button>
        ) : (
          <Button variant="ghost" onClick={onClose}>
            {" "}{traduzir("Fechar")}{" "}</Button>
        )
      }
    >
      <div className="detail-summary">
        <div>
          <span className="detail-summary__label">{" "}{traduzir("Saldo físico")}{" "}</span>
          <strong>{saldo(produto.saldoAtual, produto.unidadeMedida)}</strong>
        </div>
        <div>
          <span className="detail-summary__label">{" "}{traduzir("Valor em estoque")}{" "}</span>
          <strong>{fmtMoney(produto.valorEstoque)}</strong>
        </div>
        <div>
          <span className="detail-summary__label">{" "}{traduzir("Faixa ideal")}{" "}</span>
          <strong>
            {saldo(produto.estoqueMinimo, produto.unidadeMedida)} –{" "}
            {saldo(produto.estoqueMaximo, produto.unidadeMedida)}
          </strong>
        </div>
      </div>

      <div className="form-actions" style={{ justifyContent: "flex-end", marginTop: 0 }}>
        <LinkButton to={`/desperdicio?produto=${produto.produtoId}`} icon="trash" className="btn--raised">
          {" "}{traduzir("Registrar desperdício")}{" "}</LinkButton>
      </div>

      <Card title={traduzir("Lotes")}>
      {loading && <p className="muted">{" "}{traduzir("Carregando lotes…")}{" "}</p>}
      {lotes && lotes.length === 0 && (
        <EmptyState title={traduzir("Sem lotes")} text={traduzir("Registre uma entrada para criar o primeiro lote deste produto.")} />
      )}
      {lotes && lotes.length > 0 && (
        <DataTable
          caption={traduzir("Lotes de {0}",{0:produto.produtoNome})}
          headers={[traduzir("Código"), traduzir("Entrada"), traduzir("Validade"), traduzir("Quantidade"), traduzir("Preço unit."), traduzir("Situação")]}
        >
          {lotes.map((l) => {
            const proximo = l.proximoVencimento;
            return (
              <tr key={l.id}>
                <td>
                  <strong>{fmtLote(l.codigo, l.loteFabricante)}</strong>
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
                    {l.vencido && <StatusBadge label={traduzir("Vencido")} tone="bad" />}
                    {!l.vencido && proximo && <StatusBadge label={traduzir("Próximo do vencimento")} tone="warn" />}
                    {!l.vencido && !proximo && l.disponivel && (
                      <StatusBadge label={traduzir("Disponível")} tone="good" />
                    )}
                  </span>
                </td>
              </tr>
            );
          })}
        </DataTable>
      )}
      <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
        {" "}{traduzir("Atualizar lotes")}{" "}</Button>
      </Card>
    </Modal>
  );
}
