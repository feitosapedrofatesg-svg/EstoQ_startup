import { traduzir } from "../lib/i18n";
import { fmtLote } from "../lib/format";
import { mensagemErro } from "../lib/api";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useFetch } from "../lib/hooks";
import {
  PageHeader,
  Card,
  Button,
  DataTable,
  EmptyState,
  Field, Input,
} from "../components/UI";
import { fmtNum, fmtDate, fmtDateTime, fmtMoney, hojeISO } from "../lib/format";
import type {
  EstoqueDTO,
  MovimentacaoDTO,
  ProdutoAbertoDTO,
} from "../lib/types";
import { ConsumoRapido } from "../components/consumo/ConsumoRapido";
import { imprimirConsumo } from "../components/consumo/ImprimirConsumo";
import { useAuth } from "../store/auth";
import { useToast } from "../store/toast";
import { AbrirEmbalagemModal, AcaoProdutoAbertoModal } from "../components/ProdutoAbertoModals";
import { ITENS_POR_PAGINA, Paginacao } from "../components/Paginacao";

/** Consumo do estoque: abertura de embalagem, baixa do que foi usado e histórico do dia. */
export function Consumo() {
  const toast = useToast();
  const { user } = useAuth();
  const { data: estoque } = useFetch<EstoqueDTO[]>("/api/estoque");
  const [params] = useSearchParams();
  // Deep link do Estoque: /consumo?produto=123 já abre com o produto escolhido.
  const produtoParam = params.get("produto") ?? "";
  const [showOpen, setShowOpen] = useState(false);
  const [alvo, setAlvo] = useState<ProdutoAbertoDTO | null>(null);
  const [pagina, setPagina] = useState(0);

  const {
    data: abertos,
    loading: loadingAbertos,
    error: erroAbertos,
    refresh: refreshAbertos,
  } = useFetch<ProdutoAbertoDTO[]>("/api/produtos-abertos?finalizado=false");

  const [dia,setDia]=useState(hojeISO());
  const hoje = dia;
  const { data: registros, loading: loadingRegistros, refresh: refreshRegistros, setData: setRegistros } =
    useFetch<MovimentacaoDTO[]>(
      `/api/movimentacoes?tipo=CONSUMO&dataInicio=${hoje}&dataFim=${hoje}`
    );

  const custoHoje = useMemo(
    () => (registros ?? []).reduce((acc, m) => acc + (m.custoConsumo ?? 0), 0),
    [registros]
  );
  const registrosVisiveis = useMemo(
    () => (registros ?? []).slice(pagina * ITENS_POR_PAGINA, (pagina + 1) * ITENS_POR_PAGINA),
    [registros, pagina]
  );

  useEffect(() => {
    const totalPaginas = Math.ceil((registros?.length ?? 0) / ITENS_POR_PAGINA);
    if (pagina >= totalPaginas && pagina > 0) setPagina(Math.max(0, totalPaginas - 1));
  }, [registros, pagina]);

  const recarregar = () => {
    void refreshAbertos();
    void refreshRegistros();
  };

  return (
    <>
      <PageHeader
        title={traduzir("Consumo")}
        subtitle={traduzir("O que a cozinha usou do estoque. Abra a embalagem e registre a baixa.")}
        actions={
          <Button icon="box-open" onClick={() => setShowOpen(true)}>
            {" "}{traduzir("Abrir embalagem")}{" "}</Button>
        }
      />

      <Card title={traduzir("Novo consumo")} className="consumo-card">
        <ConsumoRapido
          produtoInicial={produtoParam}
          onDone={(resultado) => {
            setPagina(0);
            if(dia===hojeISO())setRegistros(atuais => [...resultado.movimentacoes, ...(atuais ?? []).filter(m => !resultado.movimentacoes.some(novo => novo.id === m.id))]
              .sort((a, b) => b.dataHora.localeCompare(a.dataHora) || b.id - a.id));
            recarregar();
            toast.success(traduzir("Consumo registrado"));
          }}
        />
      </Card>

      <Card title={traduzir("Embalagens abertas")}>
        {erroAbertos && <div className="alertbanner alertbanner--bad">{erroAbertos}</div>}
        {loadingAbertos && !abertos ? (
          <p className="muted">{" "}{traduzir("Carregando…")}{" "}</p>
        ) : abertos && abertos.length === 0 ? (
          <EmptyState
            title={traduzir("Nenhuma embalagem aberta")}
            text={traduzir("Ao abrir um pacote, bolsa ou vasilhame, registre aqui para controlar o aproveitamento.")}
            icon="box-open"
            action={
              <Button variant="outline" icon="box-open" onClick={() => setShowOpen(true)}>
                {" "}{traduzir("Abrir a primeira")}{" "}</Button>
            }
          />
        ) : (
          <DataTable
            caption={traduzir("Embalagens abertas na cozinha")}
            headers={[traduzir("Produto"), traduzir("Lote / validade"), traduzir("Aberta"), traduzir("Já usado"), traduzir("Resta"), traduzir("Ações")]}
          >
            {abertos?.map((p) => (
              <tr key={p.id}>
                <td>
                  <strong>{p.produtoNome}</strong>
                </td>
                <td>
                  <span className="cell-sub">
                    {" "}{traduzir("Lote")}{" "}{fmtLote(p.loteCodigo, p.loteFabricante)}{" "}{traduzir("· vence")}{" "}{fmtDate(p.dataValidade)}
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
                      {" "}{traduzir("Consumir")}{" "}</Button>
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      <Card
        title={traduzir("Consumo do dia")}
        actions={
          <div className="filters">
            <Field label={traduzir("Dia do consumo")} htmlFor="consumo-dia"><Input id="consumo-dia" type="date" value={dia} onChange={e=>{if(e.target.value){setDia(e.target.value);setPagina(0);}}} /></Field>
            <span className="muted">
              {" "}{traduzir("Custo registrado:")}{" "}<strong>{fmtMoney(custoHoje)}</strong>
            </span>
            <Button variant="outline" size="sm" icon="printer" disabled={!registros?.length || loadingRegistros} onClick={() => {
              const consolidados = new Map<string, { produto: string; quantidade: number; unidade: string; custo: number }>();
              for (const movimento of registros ?? []) {
                const unidade = movimento.unidadeMedida ?? estoque?.find(p => p.produtoId === movimento.produtoId)?.unidadeMedida;
                if (!unidade) { toast.error(traduzir("Não foi possível imprimir"), traduzir("Atualize a lista para recuperar a unidade dos produtos.")); return; }
                const chave = `${movimento.produtoId}|${unidade}`;
                const linha = consolidados.get(chave) ?? { produto: movimento.produtoNome, quantidade: 0, unidade, custo:0 };
                linha.quantidade = Math.round((linha.quantidade + movimento.quantidade) * 1000) / 1000;
                linha.custo=Math.round((linha.custo+(movimento.custoConsumo ?? 0))*100)/100;
                consolidados.set(chave, linha);
              }
              try { imprimirConsumo(user?.restauranteNome ?? "Restaurante", hoje, [...consolidados.values()].sort((a, b) => a.produto.localeCompare(b.produto))); }
              catch (e) { toast.error(traduzir("Não foi possível imprimir"), mensagemErro(e)); }
            }}>{" "}{traduzir("Imprimir lista do dia")}{" "}</Button>
            <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refreshRegistros()}>
              {" "}{traduzir("Atualizar")}{" "}</Button>
          </div>
        }
      >
        {loadingRegistros && !registros ? (
          <p className="muted">{" "}{traduzir("Carregando…")}{" "}</p>
        ) : registros && registros.length === 0 ? (
          <EmptyState
            title={traduzir("Nenhum consumo registrado neste dia")}
            text={traduzir("Use o formulário acima para registrar o que foi usado.")}
            icon="clipboard-list"
          />
        ) : (
          <>
            <DataTable caption={traduzir("Consumos registrados no dia")} headers={[traduzir("Quando"), traduzir("Produto"), traduzir("Lote"), traduzir("Quantidade"), traduzir("Custo")]}>
              {registrosVisiveis.map((m) => (
                <tr key={m.id}>
                  <td className="muted-cell">{fmtDateTime(m.dataHora)}</td>
                  <td>
                    <strong>{m.produtoNome}</strong>
                    {m.observacao && <span className="cell-sub">{m.observacao}</span>}
                  </td>
                  <td className="muted-cell">{fmtLote(m.loteCodigo, m.loteFabricante)}</td>
                  <td>{fmtNum(m.quantidade)} {m.unidadeMedida?.toLowerCase()}</td>
                  <td>{fmtMoney(m.custoConsumo ?? 0)}</td>
                </tr>
              ))}
            </DataTable>
            <Paginacao dados={null} totalItens={registros?.length ?? 0} itensPorPagina={ITENS_POR_PAGINA} pagina={pagina} alterar={setPagina} />
          </>
        )}
      </Card>

      {showOpen && (
        <AbrirEmbalagemModal
          onClose={() => setShowOpen(false)}
          onDone={() => {
            setShowOpen(false);
            recarregar();
            toast.success(traduzir("Embalagem aberta e lançada"));
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
            toast.success(traduzir("Consumo da embalagem registrado"));
          }}
        />
      )}
    </>
  );
}
