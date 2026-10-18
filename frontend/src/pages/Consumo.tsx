import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useFetch } from "../lib/hooks";
import {
  PageHeader,
  Card,
  Button,
  DataTable,
  EmptyState,
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

  const {
    data: abertos,
    loading: loadingAbertos,
    error: erroAbertos,
    refresh: refreshAbertos,
  } = useFetch<ProdutoAbertoDTO[]>("/api/produtos-abertos?finalizado=false");

  const hoje = hojeISO();
  const { data: registros, loading: loadingRegistros, refresh: refreshRegistros, setData: setRegistros } =
    useFetch<MovimentacaoDTO[]>(
      `/api/movimentacoes?tipo=CONSUMO&dataInicio=${hoje}&dataFim=${hoje}`
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

      <Card title="Novo consumo" className="consumo-card">
        <ConsumoRapido
          produtoInicial={produtoParam}
          onDone={(resultado) => {
            setRegistros(atuais => [...resultado.movimentacoes, ...(atuais ?? []).filter(m => !resultado.movimentacoes.some(novo => novo.id === m.id))]
              .sort((a, b) => b.dataHora.localeCompare(a.dataHora) || b.id - a.id));
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
            <Button variant="outline" size="sm" icon="printer" disabled={!registros?.length || loadingRegistros} onClick={() => {
              const consolidados = new Map<string, { produto: string; quantidade: number; unidade: string }>();
              for (const movimento of registros ?? []) {
                const unidade = movimento.unidadeMedida ?? estoque?.find(p => p.produtoId === movimento.produtoId)?.unidadeMedida;
                if (!unidade) { toast.error("Não foi possível imprimir", "Atualize a lista para recuperar a unidade dos produtos."); return; }
                const chave = `${movimento.produtoId}|${unidade}`;
                const linha = consolidados.get(chave) ?? { produto: movimento.produtoNome, quantidade: 0, unidade };
                linha.quantidade = Math.round((linha.quantidade + movimento.quantidade) * 1000) / 1000;
                consolidados.set(chave, linha);
              }
              try { imprimirConsumo(user?.restauranteNome ?? "Restaurante", hoje, [...consolidados.values()].sort((a, b) => a.produto.localeCompare(b.produto))); }
              catch (e) { toast.error("Não foi possível imprimir", (e as Error).message); }
            }}>Imprimir lista do dia</Button>
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
                <td>{fmtNum(m.quantidade)} {m.unidadeMedida?.toLowerCase()}</td>
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
