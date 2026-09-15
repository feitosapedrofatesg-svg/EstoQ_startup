import { useAuth } from "../store/auth";
import { useFetch } from "../lib/hooks";
import { api } from "../lib/api";
import { PageHeader, Card, StatCard, Badge, EmptyState, LinkButton, Button } from "../components/UI";
import { CmvGauge } from "../components/Charts";
import { fmtMoney, fmtPct, fmtDateTime, fmtDataHoje } from "../lib/format";
import type { AlertaDTO, DashboardResumoDTO } from "../lib/types";
import { useToast } from "../store/toast";

const ALERT_TONE: Record<AlertaDTO["tipo"], "bad" | "warn" | "info" | "good"> = {
  VENCIDO: "bad",
  PROXIMO_VENCIMENTO: "warn",
  ESTOQUE_BAIXO: "warn",
  BALANCO_PENDENTE: "info",
  DIFERENCA_ESTOQUE: "info",
};

const ALERT_LABEL: Record<AlertaDTO["tipo"], string> = {
  VENCIDO: "Vencido",
  PROXIMO_VENCIMENTO: "Próximo a vencer",
  ESTOQUE_BAIXO: "Estoque baixo",
  BALANCO_PENDENTE: "Balanço pendente",
  DIFERENCA_ESTOQUE: "Diferença no balanço",
};

export function Dashboard() {
  const { user, isAdmin, isCozinha, canReport, canMove } = useAuth();
  const { data, loading, error, refresh } = useFetch<DashboardResumoDTO>("/api/dashboard/resumo");
  const canSeeAlerts = isAdmin || isCozinha;
  const { data: alerts, refresh: refreshAlerts } = useFetch<AlertaDTO[]>(canSeeAlerts ? "/api/alertas" : null);
  const toast = useToast();

  const markSeen = async (id: number) => {
    try {
      await api.put(`/api/alertas/${id}/visualizado`);
      void refreshAlerts();
    } catch (e) {
      toast.error("Não foi possível atualizar", (e as Error).message);
    }
  };

  return (
    <>
      <PageHeader
        title={<>Olá, {user?.nome.split(" ")[0]}.</>}
        subtitle={<span className="capitalize">{fmtDataHoje()}</span>}
        actions={
          <>
            {canMove && <LinkButton to="/movimentacoes" icon="plus">Registrar movimento</LinkButton>}
            {canReport && (
              <LinkButton to="/relatorios" variant="outline" icon="chart">
                Relatórios
              </LinkButton>
            )}
          </>
        }
      />

      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          Não foi possível carregar o resumo. {error}
          <Button variant="ghost" size="sm" onClick={() => void refresh()}>
            Tentar de novo
          </Button>
        </div>
      )}

      {loading && !data && <p className="muted">Carregando visão geral…</p>}

      {data && (
        <>
          <div className="stat-grid">
            <StatCard label="Produtos ativos" value={data.totalProdutosAtivos} icon="boxes" />
            <StatCard
              label="Estoque baixo"
              value={data.produtosEstoqueBaixo}
              tone={data.produtosEstoqueBaixo > 0 ? "warn" : "neutral"}
              icon="alert-triangle"
            />
            <StatCard
              label="Próximos a vencer"
              value={data.lotesProximosVencimento}
              tone={data.lotesProximosVencimento > 0 ? "warn" : "neutral"}
              icon="clock"
            />
            <StatCard
              label="Vencidos"
              value={data.lotesVencidos}
              tone={data.lotesVencidos > 0 ? "bad" : "neutral"}
              icon="alert-circle"
            />
            <StatCard label="Embalagens abertas" value={data.produtosAbertosAtivos} tone="accent" icon="box-open" />
            <StatCard
              label="Balanços pendentes"
              value={data.balancosPendentes}
              tone={data.balancosPendentes > 0 ? "info" : "neutral"}
              icon="scale"
            />
          </div>

          <div className="grid-2">
            <Card title="CMV — custo da mercadoria vendida">
              {canReport ? (
                <div className="cmv-block">
                  <div className="cmv-block__money">{fmtMoney(data.cmvPeriodo)}</div>
                  <p className="muted">nos últimos 30 dias</p>
                  <CmvGauge
                    percentual={data.cmvPercentual}
                    ideal={data.cmvIdeal}
                    label="Percentual de CMV"
                  />
                  <div className="cmv-block__rows">
                    <div className="cmv-row">
                      <span>Desperdício no período</span>
                      <strong>{fmtMoney(data.valorDesperdicioPeriodo)}</strong>
                    </div>
                    <div className="cmv-row">
                      <span>Perdas não explicadas</span>
                      <strong className={data.perdasNaoExplicadasPeriodo > 0 ? "text-bad" : ""}>
                        {fmtMoney(data.perdasNaoExplicadasPeriodo)}
                      </strong>
                    </div>
                    <div className="cmv-row">
                      <span>Diferença para a meta</span>
                      <strong>{fmtPct(data.diferencaCmvParaMeta)}</strong>
                    </div>
                  </div>
                </div>
              ) : (
                <EmptyState
                  title="Os relatórios de CMV ficam com o nutricionista"
                  text="O perfil Cozinha registra entradas, consumo e desperdício. A análise de CMV é feita pelo nutricionista ou administrador."
                />
              )}
            </Card>

            {canSeeAlerts && (
              <Card title="Precisa de atenção">
                {alerts && alerts.length > 0 ? (
                  <ul className="alert-list">
                    {alerts.slice(0, 8).map((a) => (
                      <li key={a.id} className="alert-list__item">
                        <Badge tone={ALERT_TONE[a.tipo]}>
                          <span className="badge__dot" aria-hidden="true" />
                          {ALERT_LABEL[a.tipo]}
                        </Badge>
                        <div className="alert-list__body">
                          <p>{a.mensagem}</p>
                          <p className="alert-list__time">{fmtDateTime(a.dataGeracao)}</p>
                        </div>
                        <button
                          className="link-btn"
                          onClick={() => void markSeen(a.id)}
                        >
                          Marcar como visto
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : alerts && alerts.length === 0 ? (
                  <EmptyState
                    title="Sem alertas em aberto"
                    text="Esteja atento: aqui aparecem estoques baixos, vencimentos e diferenças no balanço."
                    icon="check-circle"
                  />
                ) : (
                  <p className="muted">Carregando alertas…</p>
                )}
              </Card>
            )}
          </div>
        </>
      )}
    </>
  );
}