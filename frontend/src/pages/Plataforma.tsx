import { useState } from "react";
import { useFetch } from "../lib/hooks";
import { api } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  Modal,
  Field,
  Input,
  DataTable,
  StatusBadge,
  EmptyState,
} from "../components/UI";
import { fmtDateTime } from "../lib/format";
import type { RestauranteDTO } from "../lib/types";
import { useToast } from "../store/toast";

/**
 * Painel da plataforma: lista as cozinhas cadastradas e permite suspender,
 * reativar e redefinir a senha do administrador de cada uma.
 */
export function Plataforma() {
  const toast = useToast();
  const [busy, setBusy] = useState("");
  const [redefinir, setRedefinir] = useState<RestauranteDTO | null>(null);

  const { data, loading, error, refresh } = useFetch<RestauranteDTO[]>(
    "/api/plataforma/restaurantes"
  );
  const restaurantes = data ?? [];

  const alternarSituacao = async (r: RestauranteDTO) => {
    const acao = r.ativo ? "suspender" : "reativar";
    setBusy("situacao" + r.id);
    try {
      await api.put(`/api/plataforma/restaurantes/${r.id}`, { ativo: !r.ativo });
      toast.success(r.ativo ? `${r.nome} suspensa` : `${r.nome} reativada`);
      void refresh();
    } catch (e) {
      toast.error(`Não foi possível ${acao}`, (e as Error).message);
    } finally {
      setBusy("");
    }
  };

  return (
    <>
      <PageHeader
        title="Restaurantes"
        subtitle="Cozinhas cadastradas na plataforma. Suspender bloqueia o acesso de toda a equipe na hora."
      />

      <Card title="Cozinhas">
        {error && <div className="alertbanner alertbanner--bad">{error}</div>}
        {loading && !data && <p className="muted">Carregando…</p>}
        {!loading && restaurantes.length === 0 ? (
          <EmptyState
            title="Nenhuma cozinha ainda"
            text="Quando uma cozinha criar a própria conta, ela aparece aqui."
          />
        ) : (
          <DataTable
            caption="Cozinhas cadastradas"
            headers={["Nome", "Criada em", "Usuários", "Situação", ""]}
          >
            {restaurantes.map((r) => (
              <tr key={r.id}>
                <td>
                  <strong>{r.nome}</strong>
                </td>
                <td className="muted-cell">{fmtDateTime(r.dataHoraCriacao)}</td>
                <td className="muted-cell">{r.totalUsuarios}</td>
                <td>
                  {r.ativo ? (
                    <StatusBadge label="Ativa" tone="good" />
                  ) : (
                    <StatusBadge label="Suspensa" tone="bad" />
                  )}
                </td>
                <td>
                  <div className="td-actions">
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={r.ativo ? "x" : "check"}
                      loading={busy === "situacao" + r.id}
                      onClick={() => void alternarSituacao(r)}
                      aria-label={r.ativo ? `Suspender ${r.nome}` : `Reativar ${r.nome}`}
                    >
                      {r.ativo ? "Suspender" : "Reativar"}
                    </Button>
                    <Button size="sm" variant="ghost" icon="edit" onClick={() => setRedefinir(r)}>
                      Redefinir senha
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
        <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
          Atualizar
        </Button>
      </Card>

      {redefinir && (
        <RedefinirSenhaModal
          restaurante={redefinir}
          onClose={() => setRedefinir(null)}
          onDone={() => {
            setRedefinir(null);
            void refresh();
          }}
        />
      )}
    </>
  );
}

function RedefinirSenhaModal({
  restaurante,
  onClose,
  onDone,
}: {
  restaurante: RestauranteDTO;
  onClose: () => void;
  onDone: () => void;
}) {
  const toast = useToast();
  const [senha, setSenha] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (senha.length < 8) {
      setError("A nova senha precisa ter ao menos 8 caracteres.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await api.post(`/api/plataforma/restaurantes/${restaurante.id}/redefinir-admin`, { senha });
      toast.success(`Senha do administrador de ${restaurante.nome} redefinida`);
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={`Redefinir senha — ${restaurante.nome}`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button onClick={() => void submit()} loading={submitting}>
            Salvar nova senha
          </Button>
        </>
      }
    >
      <div className="alertbanner alertbanner--warn" role="note">
        Isso altera a senha do administrador da cozinha na hora. Avise o responsável.
      </div>
      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          {error}
        </div>
      )}
      <Field label="Nova senha do administrador" required hint="Ao menos 8 caracteres.">
        <Input
          type="password"
          autoComplete="new-password"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          placeholder="Senha com 8+ caracteres"
        />
      </Field>
    </Modal>
  );
}