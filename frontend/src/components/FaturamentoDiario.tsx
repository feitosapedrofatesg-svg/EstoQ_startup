import { traduzir } from "../lib/i18n";
import { mensagemErro } from "../lib/api";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { api } from "../lib/api";
import { useFetch } from "../lib/hooks";
import { fmtDate, fmtMoney, hojeISO, parseDecimal } from "../lib/format";
import type { FaturamentoDiarioDTO, FaturamentoPeriodoDTO } from "../lib/types";
import { Button, DataTable, Field, Input } from "./UI";
import { ListaComRolagem } from "../responsividade/tablet/ListaComRolagem";
import { useToast } from "../store/toast";

export function AvisoFaturamento({ receitaBase, diasFaturamentoPendentes, receitaPorPeriodo }: {
  receitaBase: number | null; diasFaturamentoPendentes: number; receitaPorPeriodo: boolean;
}) {
  if (receitaPorPeriodo) return <p className="muted">{" "}{traduzir("O percentual usa o total informado anteriormente para este período. Os valores diários ainda estão incompletos.")}{" "}</p>;
  if (diasFaturamentoPendentes > 0) return <p className="alertbanner alertbanner--warn" role="status">
    {" "}{traduzir("Percentual pendente: faltam")}{" "}{diasFaturamentoPendentes}{" "}{traduzir("dia(s) de faturamento. Informe R$ 0 nos dias sem vendas.")}{" "}</p>;
  if (receitaBase === 0) return <p className="muted">{" "}{traduzir("Faturamento total igual a zero: não é possível calcular o percentual do CMV.")}{" "}</p>;
  return null;
}

export function FaturamentoDiario({ inicio, fim, historico = false }: { inicio: string; fim: string; historico?: boolean }) {
  const { data, loading, error, refresh, setData } = useFetch<FaturamentoPeriodoDTO>(
    `/api/relatorios/faturamento-diario?inicio=${inicio}&fim=${fim}`);
  const [dia, setDia] = useState(() => hojeISO() >= inicio && hojeISO() <= fim ? hojeISO() : fim);
  const [valor, setValor] = useState("");
  const [alterado, setAlterado] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);
  const [mostrarDias, setMostrarDias] = useState(false);
  const toast = useToast();
  const salvo = data?.dias?.find(d => d.data === dia);
  const [versao, setVersao] = useState<number | null>(null);
  const formulario = useRef<HTMLFormElement>(null);

  useEffect(() => {
    // Uma atualização em segundo plano não substitui o rascunho nem sua versão.
    if (!alterado) {
      setValor(salvo?.valor == null ? "" : String(salvo.valor).replace(".", ","));
      setVersao(salvo?.version ?? null);
    }
  }, [salvo?.valor, salvo?.version, alterado, dia]);

  const selecionar = (novaData: string) => {
    if (!novaData || novaData < inicio || novaData > fim) return;
    setDia(novaData); setAlterado(false); setFalha(null);
  };
  const editar = (novaData: string) => {
    selecionar(novaData);
    const campo = formulario.current?.querySelector<HTMLInputElement>("#faturamento-valor");
    if (!campo) return;
    const reduzirMovimento = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    campo.focus({ preventScroll: true });
    // Centraliza depois que a seleção do dia e o foco atualizarem a tela.
    window.requestAnimationFrame(() => {
      campo.scrollIntoView({ behavior: reduzirMovimento ? "instant" : "smooth", block: "center" });
    });
  };
  const salvar = async (evento: FormEvent) => {
    evento.preventDefault();
    const numero = parseDecimal(valor);
    if (valor.trim() && (numero === null || numero < 0)) {
      setFalha("Informe um faturamento válido, maior ou igual a zero."); return;
    }
    setSalvando(true); setFalha(null);
    try {
      const resposta = await api.put<FaturamentoDiarioDTO>("/api/relatorios/faturamento-diario", { data: dia, valor: numero, version: versao });
      // Mantém o valor confirmado mesmo se a consulta dos totais falhar depois.
      setData(anterior => anterior ? { ...anterior, dias: anterior.dias.map(d => d.data === resposta.data ? resposta : d) } : anterior);
      await refresh(); setAlterado(false);
      toast.success(traduzir("Faturamento do dia salvo"));
    } catch (e) { setFalha(mensagemErro(e)); }
    finally { setSalvando(false); }
  };

  return <section aria-label={traduzir("Faturamento diário")} className="faturamento-diario">
    <h3>{" "}{traduzir("Faturamento por dia")}{" "}</h3>
    <p className="muted">{" "}{traduzir("Informe o total de vendas de cada dia. R$ 0 significa dia sem vendas; vazio significa não informado.")}{" "}</p>
    {error && <p className="alertbanner alertbanner--bad" role="alert">{" "}{traduzir("Não foi possível carregar o faturamento.")}{" "}{traduzir(error)}</p>}
    {loading && <p className="muted">{" "}{traduzir("Carregando faturamento…")}{" "}</p>}
    <form ref={formulario} className="form-grid" onSubmit={evento => void salvar(evento)}>
      <Field label={traduzir("Dia do faturamento")} htmlFor="faturamento-dia">
        <Input id="faturamento-dia" type="date" min={inicio} max={fim} value={dia}
          disabled={salvando} onChange={e => selecionar(e.target.value)} />
      </Field>
      <Field label={traduzir("Faturamento do dia (R$)")} htmlFor="faturamento-valor" error={falha}>
        <Input id="faturamento-valor" inputMode="decimal" value={valor} placeholder={traduzir("Não informado")}
          disabled={loading || salvando || !!error} invalid={!!falha}
          onChange={e => { setValor(e.target.value); setAlterado(true); setFalha(null); }} />
      </Field>
      <Button type="submit" variant="good" loading={salvando} disabled={loading || !!error || !alterado}>{" "}{traduzir("Salvar faturamento")}{" "}</Button>
      <Button type="button" variant="ghost" disabled={salvando} onClick={() => {
        setAlterado(false); setFalha(null); void refresh();
      }}>{" "}{traduzir("Recarregar faturamento")}{" "}</Button>
    </form>
    {data?.dias && <p className="muted">{data.diasInformados}{" "}{traduzir("de")}{" "}{data.dias.length}{" "}{traduzir("dias informados. Total diário informado:")}{" "}{fmtMoney(data.totalInformado)}.</p>}
    {historico && data?.dias && <>
      <Button variant="outline" size="sm" icon="chevron-down" className="faturamento-diario__toggle"
        aria-expanded={mostrarDias} onClick={() => setMostrarDias(!mostrarDias)}>
        {mostrarDias ? traduzir("Ocultar dias do período") : traduzir("Ver dias do período")}
      </Button>
      {mostrarDias && <ListaComRolagem quantidade={data.dias.length}>
        <DataTable caption={traduzir("Faturamento por data")} headers={[traduzir("Data"), traduzir("Faturamento"), ""]}>
          {data.dias.map(d => <tr key={d.data}><td>{fmtDate(d.data)}</td>
            <td>{d.valor == null ? traduzir("Não informado") : fmtMoney(d.valor)}</td>
            <td><Button size="sm" variant="ghost" onClick={() => editar(d.data)} disabled={salvando}
              aria-label={traduzir("Editar faturamento de {0}",{0:fmtDate(d.data)})}>{" "}{traduzir("Editar")}{" "}</Button></td></tr>)}
        </DataTable>
      </ListaComRolagem>}
    </>}
  </section>;
}
