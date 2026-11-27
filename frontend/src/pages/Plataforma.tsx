import { traduzir } from "../lib/i18n";
import { mensagemErro } from "../lib/api";
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
      toast.error(traduzir("Não foi possível {0}",{0:acao}), mensagemErro(e));
    } finally {
      setBusy("");
    }
  };

  return (
    <>
      <PageHeader
        title={traduzir("Restaurantes")}
        subtitle={traduzir("Cozinhas cadastradas na plataforma. Suspender bloqueia o acesso de toda a equipe na hora.")}
      />

      <Card collapsible title={traduzir("Cozinhas")}>
        {error && <div className="alertbanner alertbanner--bad">{traduzir(error)}</div>}
        {loading && !data && <p className="muted">{" "}{traduzir("Carregando…")}{" "}</p>}
        {!loading && restaurantes.length === 0 ? (
          <EmptyState
            title={traduzir("Nenhuma cozinha ainda")}
            text={traduzir("Quando uma cozinha criar a própria conta, ela aparece aqui.")}
          />
        ) : (
          <DataTable
            caption={traduzir("Cozinhas cadastradas")}
            headers={[traduzir("Nome"), traduzir("Criada em"), traduzir("Usuários"), traduzir("Situação"), ""]}
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
                    <StatusBadge label={traduzir("Ativa")} tone="good" />
                  ) : (
                    <StatusBadge label={traduzir("Suspensa")} tone="bad" />
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
                      aria-label={r.ativo ? traduzir("Suspender {0}",{0:r.nome}) : traduzir("Reativar {0}",{0:r.nome})}
                    >
                      {r.ativo ? traduzir("Suspender") : traduzir("Reativar")}
                    </Button>
                    <Button size="sm" variant="ghost" icon="edit" onClick={() => setRedefinir(r)}>
                      {" "}{traduzir("Redefinir senha")}{" "}</Button>
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
        <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
          {" "}{traduzir("Atualizar")}{" "}</Button>
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
  const [confirmarSenha, setConfirmarSenha] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (senha.length < 8) {
      setError(traduzir("A nova senha precisa ter ao menos 8 caracteres."));
      return;
    }
    if (senha !== confirmarSenha) {
      setError(traduzir("As senhas não conferem."));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await api.post(`/api/plataforma/restaurantes/${restaurante.id}/redefinir-admin`, { senha });
      toast.success(traduzir("Senha do administrador de {0} redefinida",{0:restaurante.nome}));
      onDone();
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
      title={traduzir("Redefinir senha — {0}",{0:restaurante.nome})}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            {" "}{traduzir("Cancelar")}{" "}</Button>
          <Button onClick={() => void submit()} loading={submitting}>
            {" "}{traduzir("Salvar nova senha")}{" "}</Button>
        </>
      }
    >
      <div className="alertbanner alertbanner--warn" role="note">
        {" "}{traduzir("Isso altera a senha do administrador da cozinha na hora. Avise o responsável.")}{" "}</div>
      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          {traduzir(error)}
        </div>
      )}
      <Field label={traduzir("Nova senha do administrador")} htmlFor="plataforma-senha" required hint={traduzir("Ao menos 8 caracteres.")}>
        <Input
          id="plataforma-senha"
          type="password"
          autoComplete="new-password"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          placeholder={traduzir("Senha com 8+ caracteres")}
        />
      </Field>
      <Field label={traduzir("Confirmar senha")} htmlFor="plataforma-confirmar-senha" required>
        <Input
          id="plataforma-confirmar-senha"
          type="password"
          autoComplete="new-password"
          value={confirmarSenha}
          onChange={(e) => setConfirmarSenha(e.target.value)}
          placeholder={traduzir("Repita a senha")}
        />
      </Field>
    </Modal>
  );
}
