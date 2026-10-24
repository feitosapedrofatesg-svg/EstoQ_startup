import { mensagemErro } from "../../lib/api";
import { useState } from "react";

import { useTodasPaginas } from "../../lib/hooks";
import { api } from "../../lib/api";
import { Button, Modal, Field, Select } from "../UI";

import type { BalancoDTO, CategoriaDTO, TipoBalanco } from "../../lib/types";






export function NovoBalanco({ onClose, onDone }: { onClose: () => void; onDone: (criado: BalancoDTO) => void }) {
  const [tipo, setTipo] = useState<TipoBalanco>("GERAL");
  const { data: categorias } = useTodasPaginas<CategoriaDTO>("/api/categorias?sort=nome,asc");
  const cats = categorias ?? [];
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const toggle = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const criado = await api.post<BalancoDTO>("/api/balancos", {
        tipo,
        ...(tipo === "PARCIAL" ? { categorias: [...selected] } : {}),
      });
      onDone(criado);
    } catch (e) {
      setError(mensagemErro(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Novo balanço"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button
            onClick={() => void submit()}
            loading={submitting}
            disabled={tipo === "PARCIAL" && selected.size === 0}
          >
            Criar balanço
          </Button>
        </>
      }
    >
      <Field label="Abrangência" htmlFor="bal-tipo">
        <Select id="bal-tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoBalanco)}>
          <option value="GERAL">Geral — todos os produtos ativos</option>
          <option value="PARCIAL">Parcial — por categoria(s) de alimento</option>
        </Select>
      </Field>
      {tipo === "PARCIAL" && (
        <div className="bal-tipo-cats">
          {cats.length === 0 && <p className="muted" style={{ marginTop: 8 }}>Carregando categorias…</p>}
          {cats.map((c) => (
            <label key={c.id} className="bal-tipo-cat">
              <input
                type="checkbox"
                checked={selected.has(c.id)}
                onChange={() => toggle(c.id)}
              />
              <span>{c.nome}</span>
            </label>
          ))}
          {cats.length > 0 && (
            <p className="muted" style={{ marginTop: 8 }}>
              Selecione uma ou mais categorias. Só os itens dessas categorias entrarão no balanço.
            </p>
          )}
        </div>
      )}
      <p className="muted" style={{ marginTop: 8 }}>
        Depois de criar, use "Iniciar contagem" para gerar a lista de itens.
      </p>
      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          {error}
        </div>
      )}
    </Modal>
  );
}
