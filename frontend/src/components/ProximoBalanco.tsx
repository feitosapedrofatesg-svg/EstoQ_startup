import { traduzir } from "../lib/i18n";
import { Card, AlertBanner } from "./UI";
import { useFetch } from "../lib/hooks";
import { fmtDate, hojeISO } from "../lib/format";
import type { ConfiguracaoBalancoDTO } from "../lib/types";

export function ProximoBalanco({ revision = 0 }: { revision?: number }) {
  const { data, loading, error } = useFetch<ConfiguracaoBalancoDTO[]>("/api/balancos/proximo", [revision], true);
  const dataAgendada = data?.map(c => c.proximaExecucao).filter((d): d is string => !!d).sort()[0];
  const hoje = hojeISO();
  return (
    <Card title={traduzir("Próximo balanço")}>
      {error && !data ? <AlertBanner tone="bad">{" "}{traduzir("Não foi possível carregar o agendamento.")}{" "}{traduzir(error)}</AlertBanner>
        : loading && !data ? <p className="muted">{" "}{traduzir("Carregando agendamento…")}{" "}</p>
        : dataAgendada ? <AlertBanner tone={dataAgendada <= hoje ? "warn" : "info"}>
          <strong>{fmtDate(dataAgendada)}</strong>
          {dataAgendada === hoje ? traduzir(" — O balanço está marcado para hoje.")
            : dataAgendada < hoje ? traduzir(" — Balanço atrasado. Realize a contagem do estoque.")
            : traduzir(" — Data programada para a próxima contagem do estoque.")}
        </AlertBanner>
        : <p className="muted">{" "}{traduzir("Nenhum balanço agendado. O administrador pode definir a frequência na tela de balanços.")}{" "}</p>}
    </Card>
  );
}
