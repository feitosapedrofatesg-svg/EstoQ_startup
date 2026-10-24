import { mensagemErro } from "../../lib/api";
import { useState } from "react";


import { api } from "../../lib/api";
import { Card, Button, Field, Select } from "../UI";

import type { ConfiguracaoBalancoDTO, PeriodicidadeBalanco } from "../../lib/types";
import { useToast } from "../../store/toast";




import { MiniCalendar } from "./CalendarioBalanco";

export function ConfigBalanco({
  config,
  onDone,
}: {
  config: ConfiguracaoBalancoDTO | null;
  onDone: () => void;
}) {
  const toast = useToast();
  const [periodicidade, setPeriodicidade] = useState<PeriodicidadeBalanco>(
    config?.periodicidade ?? "SEMANAL"
  );
  const [dia, setDia] = useState<number | null>(
    config?.diaExecucao ?? null
  );
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    const body = {
      periodicidade,
      diaExecucao: periodicidade === "DIARIA" ? null : dia,
      proximaExecucao: null,
      ...(config ? { id: config.id, version: config.version } : {}),
    };
    try {
      if (config) {
        await api.put(`/api/configuracoes-balanco/${config.id}`, body);
        toast.success("Agendamento atualizado");
      } else {
        await api.post("/api/configuracoes-balanco", body);
        toast.success("Agendamento criado");
      }
      onDone();
    } catch (e) {
      toast.error("Não foi possível salvar", mensagemErro(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card title="Agendamento do balanço" bodyClassName="config-form">
      <div className="form-grid-2">
        <Field label="Frequência">
          <Select value={periodicidade} onChange={(e) => {
            const v = e.target.value as PeriodicidadeBalanco;
            setPeriodicidade(v);
            if (v === "DIARIA") setDia(null);
            else if (dia === null) setDia(v === "SEMANAL" ? 1 : 1);
          }}>
            <option value="DIARIA">Diária</option>
            <option value="SEMANAL">Semanal</option>
            <option value="MENSAL">Mensal</option>
          </Select>
        </Field>
      </div>
      {periodicidade !== "DIARIA" && (
        <div style={{ marginTop: 12 }}>
          <MiniCalendar
            periodicidade={periodicidade}
            value={dia}
            onChange={setDia}
          />
        </div>
      )}
      <div className="config-form__btn" style={{ marginTop: 16 }}>
        <Button
          onClick={() => void save()}
          loading={saving}
          variant="outline"
          disabled={periodicidade !== "DIARIA" && dia === null}
        >
          {config ? "Salvar agendamento" : "Criar agendamento"}
        </Button>
      </div>
    </Card>
  );
}
