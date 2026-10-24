import { useEffect, useRef, useState } from "react";



import { Button } from "../UI";
import { fmtNum, parseDecimal } from "../../lib/format";
import type { BalancoDTO, ItemBalancoDTO } from "../../lib/types";






export function ItensContagem({ balanco, onItem, onDirty, busy }: {
  balanco: BalancoDTO;
  onItem: (itemId: number, quantidadeFisica: number, version: number) => Promise<ItemBalancoDTO | null>;
  onDirty: (dirty: boolean) => void;
  busy: boolean;
}) {
  type Rascunho = { texto: string; version: number; editando: boolean };
  const inicial = (item: ItemBalancoDTO): Rascunho => ({
    texto: fmtNum(item.quantidadeFisica ?? item.quantidadeSistema), version: item.version, editando: false,
  });
  const [vals, setVals] = useState<Record<number, Rascunho>>(() => Object.fromEntries(balanco.itens.map(i => [i.id, inicial(i)])));
  const atual = useRef(balanco);
  atual.current = balanco;
  const callbackDirty = useRef(onDirty);
  callbackDirty.current = onDirty;
  useEffect(() => {
    setVals(prev => Object.fromEntries(balanco.itens.map(i => [i.id, prev[i.id]?.editando ? prev[i.id] : inicial(i)])));
  }, [balanco.itens]);
  const dirty = Object.values(vals).some(r => r.editando);
  useEffect(() => { callbackDirty.current(dirty); }, [dirty]);

  const confirmItem = async (item: ItemBalancoDTO, n: number) => {
    const rascunho = vals[item.id] ?? inicial(item);
    const salvo = await onItem(item.id, n, rascunho.version);
    if (salvo) {
      const recente = atual.current.itens.find(i => i.id === item.id);
      const resultado = recente && recente.version > salvo.version ? recente : salvo;
      setVals(prev => ({ ...prev, [item.id]: inicial(resultado) }));
    }
  };
  const isConfirmed = (item: ItemBalancoDTO) => item.quantidadeFisica !== null
    && parseDecimal(vals[item.id]?.texto ?? "") === Number(item.quantidadeFisica)
    && vals[item.id]?.version === item.version;

  return (
    <div className="contagem">
      <div className="contagem__bar">
        <span className="contagem__label">Contagem</span>
      </div>
      <div className="contagem__table" role="table" aria-label={`Contagem do balanço ${balanco.id}`}>
        <div className="contagem__headers" role="row">
          <span role="columnheader">Produto</span>
          <span role="columnheader">Sistema</span>
          <span role="columnheader">Contagem física</span>
          <span role="columnheader">Resultado</span>
          <span role="columnheader" />
        </div>
        <div className="contagem__rows" role="rowgroup">
          {balanco.itens.map((i) => {
            const rascunho = vals[i.id] ?? inicial(i);
            const conflito = rascunho.editando && rascunho.version !== i.version;
            const n = parseDecimal(rascunho.texto);
            const valid = n !== null && n >= 0;
            const diff = valid && i.quantidadeSistema !== null ? n - i.quantidadeSistema : null;
            return (
              <div className="contagem__row" role="row" key={i.id}>
                <strong className="contagem__product" role="cell">
                  {i.produtoNome}
                </strong>
                <div className="contagem__cell" role="cell">
                  <span className="contagem__cell-label">Sistema</span>
                  <span>{fmtNum(i.quantidadeSistema)} {i.unidadeMedida.toLowerCase()}</span>
                </div>
                <div className="contagem__cell" role="cell">
                  <span className="contagem__cell-label">Contagem física</span>
                  <div className="contagem__input">
                    <input
                      className="input"
                      inputMode="decimal"
                      aria-label={`Contagem de ${i.produtoNome}`}
                      value={rascunho.texto}
                      disabled={busy}
                      onChange={(e) => setVals(prev => ({ ...prev, [i.id]: {
                        texto: e.target.value, version: prev[i.id]?.editando ? prev[i.id].version : i.version, editando: true,
                      } }))}
                      placeholder="0"
                    />
                    <span className="contagem__unit">{i.unidadeMedida.toLowerCase()}</span>
                  </div>
                  {conflito && <div role="alert">
                    Esta contagem mudou em outra sessão.
                    <Button size="sm" variant="ghost" onClick={() => setVals(prev => ({ ...prev, [i.id]: inicial(i) }))}>
                      Recarregar contagem
                    </Button>
                  </div>}
                </div>
                <div className="contagem__cell" role="cell">
                  <span className="contagem__cell-label">Resultado</span>
                  <span
                    className={
                      diff === null
                        ? "muted"
                        : diff === 0
                          ? "text-good"
                          : diff < 0
                            ? "text-bad"
                            : "text-good"
                    }
                  >
                    {diff === null ? "—" : `${diff > 0 ? "+" : ""}${fmtNum(diff)} ${i.unidadeMedida.toLowerCase()}`}
                  </span>
                </div>
                <div className="contagem__confirm" role="cell">
                  <Button
                    size="sm"
                    variant={isConfirmed(i) ? "good" : "outline"}
                    disabled={isConfirmed(i) || !valid || busy || conflito}
                    icon={isConfirmed(i) ? "check-circle" : undefined}
                    onClick={() => { if (valid && !conflito) void confirmItem(i, n); }}
                  >
                    OK
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
