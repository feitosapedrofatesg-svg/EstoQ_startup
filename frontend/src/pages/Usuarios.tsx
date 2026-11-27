import { traduzir } from "../lib/i18n";
import { mensagemErro } from "../lib/api";
import { useEffect, useState } from "react";
import { useAuth } from "../store/auth";
import { useFetch, useDebounced } from "../lib/hooks";
import { Paginacao } from "../components/Paginacao";
import { api } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  Modal,
  Confirm,
  Field,
  Input,
  Select,
  DataTable,
  StatusBadge,
  EmptyState,
} from "../components/UI";
import { fmtDateTime } from "../lib/format";
import type { Page, Perfil, UsuarioResponseDTO } from "../lib/types";
import { PERFIL_LABEL } from "../lib/perfis";
import { useToast } from "../store/toast";

const PERFIS: Perfil[] = ["ADMIN", "COZINHA", "OPERACIONAL", "NUTRICIONISTA"];

const PERFIL_TONE: Record<Perfil, "neutral" | "accent" | "info" | "good"> = {
  ADMIN: "neutral",
  COZINHA: "accent",
  OPERACIONAL: "accent",
  NUTRICIONISTA: "info",
  PLATAFORMA: "good",
};

export function Usuarios() {
  const { user } = useAuth();
  const toast = useToast();
  const [search, setSearch] = useState("");
  const [modal, setModal] = useState<{ open: boolean; u: UsuarioResponseDTO | null }>({
    open: false,
    u: null,
  });
  const [del, setDel] = useState<UsuarioResponseDTO | null>(null);
  const [busy, setBusy] = useState("");

  const [pagina, setPagina] = useState(0);
  const busca = useDebounced(search.trim());
  useEffect(() => { setPagina(0); }, [busca]);
  const { data, loading, error, refresh } = useFetch<Page<UsuarioResponseDTO>>(
    `/api/usuarios?size=50&page=${pagina}&q=${encodeURIComponent(busca)}&sort=nome,asc`);
  const users = data?.content ?? [];
  const rows = users;
  useEffect(() => {
    if (data && pagina >= data.totalPages && pagina > 0) setPagina(Math.max(0, data.totalPages - 1));
  }, [data, pagina]);

  const toggleAtivo = async (u: UsuarioResponseDTO) => {
    setBusy("toggle" + u.id);
    try {
      await api.put(`/api/usuarios/${u.id}`, {
        version: u.version,
        nome: u.nome,
        email: u.email,
        perfil: u.perfil,
        ativo: !u.ativo,
      });
      toast.success(u.ativo ? "Usuário desativado" : "Usuário ativado");
      void refresh();
    } catch (e) {
      toast.error(traduzir("Não foi possível atualizar"), mensagemErro(e));
    } finally {
      setBusy("");
    }
  };

  const remover = async () => {
    if (!del) return;
    setBusy("del" + del.id);
    try {
      await api.del(`/api/usuarios/${del.id}`);
      setDel(null);
      toast.success(traduzir("Usuário removido"));
      void refresh();
    } catch (e) {
      toast.error(traduzir("Não foi possível remover"), mensagemErro(e));
    } finally {
      setBusy("");
    }
  };

  return (
    <>
      <PageHeader
        title={traduzir("Usuários")}
        subtitle={traduzir("Quem acessa o estoQ e o que cada perfil pode fazer.")}
        actions={
          <Button icon="plus" onClick={() => setModal({ open: true, u: null })}>
            {" "}{traduzir("Novo usuário")}{" "}</Button>
        }
      />

      <Card
        title={traduzir("Equipe")}
        actions={
          <Input
            type="search"
            placeholder={traduzir("Buscar por nome ou e-mail…")}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label={traduzir("Buscar usuário")}
          />
        }
      >
        {error && <div className="alertbanner alertbanner--bad">{traduzir(error)}</div>}
        {loading && !data && <p className="muted">{" "}{traduzir("Carregando…")}{" "}</p>}
        {!loading && users.length === 0 ? (
          <EmptyState
            title={traduzir("Nenhum usuário")}
            text={traduzir("Adicione os perfis que vão operar o sistema: Administrador, Operacional e Nutricionista.")}
          />
        ) : (
          <DataTable caption={traduzir("Usuários do sistema")} headers={[traduzir("Nome"), traduzir("E-mail"), traduzir("Perfil"), traduzir("Situação"), traduzir("Criado em"), ""]}>
            {rows.map((u) => (
              <tr key={u.id}>
                <td>
                  <strong>{u.nome}</strong>
                  {u.id === user?.id && <span className="cell-sub">{" "}{traduzir("é você")}{" "}</span>}
                </td>
                <td className="muted-cell">{u.email}{u.googleVinculado && <span className="cell-sub">{" "}{traduzir("Acesso com Google")}{" "}</span>}{u.confirmacaoEmailPendente && <small className="muted" style={{ display: "block" }}>{" "}{traduzir("Confirmação de e-mail pendente")}{" "}</small>}</td>
                <td>
                  <StatusBadge tone={PERFIL_TONE[u.perfil]}>{traduzir(PERFIL_LABEL[u.perfil])}</StatusBadge>
                </td>
                <td>
                  {u.ativo ? (
                    <StatusBadge label={traduzir("Ativo")} tone="good" />
                  ) : (
                    <StatusBadge label={traduzir("Inativo")} tone="neutral" />
                  )}
                </td>
                <td className="muted-cell">{fmtDateTime(u.dataHoraCriacao)}</td>
                <td>
                  <div className="td-actions">
                    <Button size="sm" variant="ghost" icon="edit" onClick={() => setModal({ open: true, u })}>
                      {" "}{traduzir("Editar")}{" "}</Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={u.ativo ? "x" : "check"}
                      loading={busy === "toggle" + u.id}
                      onClick={() => void toggleAtivo(u)}
                      aria-label={u.ativo ? traduzir("Desativar {0}",{0:u.nome}) : traduzir("Ativar {0}",{0:u.nome})}
                    />
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
        <Paginacao dados={data} pagina={pagina} alterar={setPagina} />
        <Button variant="ghost" size="sm" icon="refresh" onClick={() => void refresh()}>
          {" "}{traduzir("Atualizar")}{" "}</Button>
      </Card>

      {modal.open && (
        <UsuarioForm
          u={modal.u}
          onClose={() => setModal({ open: false, u: null })}
          onDone={() => {
            setModal({ open: false, u: null });
            void refresh();
          }}
        />
      )}

      <Confirm
        open={!!del}
        title={traduzir("Remover usuário")}
        message={traduzir("Desativar \"{0}\"? Ela/e não conseguirá mais entrar no sistema. O histórico fica preservado.",{0:del?.nome})}
        danger
        confirmLabel={traduzir("Desativar")}
        loading={busy === "del" + del?.id}
        onConfirm={() => void remover()}
        onClose={() => setDel(null)}
      />
    </>
  );
}

function UsuarioForm({
  u,
  onClose,
  onDone,
}: {
  u: UsuarioResponseDTO | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const toast = useToast();
  const [nome, setNome] = useState(u?.nome ?? "");
  const [email, setEmail] = useState(u?.email ?? "");
  const [perfil, setPerfil] = useState<Perfil>(u?.perfil ?? "COZINHA");
  const [senha, setSenha] = useState("");
  const [confirmarSenha, setConfirmarSenha] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (!nome.trim()) return setError(traduzir("Informe o nome."));
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return setError(traduzir("Informe um e-mail válido."));
    if (!u && senha.length < 8) return setError(traduzir("A senha precisa ter ao menos 8 caracteres."));
    if (u && senha && senha.length < 8) return setError(traduzir("A senha precisa ter ao menos 8 caracteres."));
    if (senha !== confirmarSenha) return setError(traduzir("As senhas não conferem."));
    if (u?.googleVinculado && u.possuiSenha === false && email.trim().toLowerCase() !== u.email.toLowerCase() && !senha)
      return setError(traduzir("Defina uma senha para alterar o e-mail de uma conta que entra somente com Google."));
    setSubmitting(true);
    setError(null);
    try {
      if (u) {
        const atualizado = await api.put<UsuarioResponseDTO>(`/api/usuarios/${u.id}`, {
          version: u.version,
          nome: nome.trim(),
          email: email.trim().toLowerCase(),
          perfil,
          senha: senha || null,
          ativo: u.ativo,
        });
        toast.success(traduzir("Usuário atualizado"), atualizado?.confirmacaoEmailPendente ? "O usuário precisa confirmar o e-mail para entrar. Pode solicitar outro link na tela de acesso." : undefined);
      } else {
        const criado = await api.post<UsuarioResponseDTO>("/api/usuarios", {
          nome: nome.trim(),
          email: email.trim().toLowerCase(),
          senha,
          perfil,
        });
        toast.success(traduzir("Usuário criado"), criado?.confirmacaoEmailPendente ? "O usuário precisa confirmar o e-mail para entrar." : undefined);
      }
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
      title={u ? traduzir("Editar {0}",{0:u.nome}) : traduzir("Novo usuário")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            {" "}{traduzir("Cancelar")}{" "}</Button>
          <Button onClick={() => void submit()} loading={submitting}>
            {" "}{traduzir("Salvar")}{" "}</Button>
        </>
      }
    >
      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          {traduzir(error)}
        </div>
      )}
      <Field label={traduzir("Nome")} required>
        <Input value={nome} onChange={(e) => setNome(e.target.value)} placeholder={traduzir("Nome e sobrenome")} />
      </Field>
      <Field label={traduzir("E-mail")} required hint={u?.googleVinculado ? traduzir("Alterar o e-mail desvincula o Google. Se o acesso for somente pelo Google, defina também uma senha.") : undefined}>
        <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder={traduzir("pessoa@restaurante.com")} />
      </Field>
      <Field label={traduzir("Perfil")} required>
        <Select value={perfil} onChange={(e) => setPerfil(e.target.value as Perfil)}>
          {PERFIS.map((p) => (
            <option key={p} value={p}>
              {traduzir(PERFIL_LABEL[p])} — {p === "ADMIN" ? traduzir("tudo") : p === "OPERACIONAL" ? traduzir("cozinha e entrada de itens") : p === "COZINHA" ? traduzir("estoque e operação diária") : traduzir("CMV e relatórios")}
            </option>
          ))}
        </Select>
      </Field>
      <Field label={u ? traduzir("Nova senha (deixe vazio para manter)") : traduzir("Senha")} htmlFor="usuario-senha" required={!u} hint={traduzir("Ao menos 8 caracteres.")}>
        <Input
          id="usuario-senha"
          type="password"
          autoComplete="new-password"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          placeholder={u ? "••••••••" : traduzir("Senha com 8+ caracteres")}
        />
      </Field>
      <Field label={traduzir("Confirmar senha")} htmlFor="usuario-confirmar-senha" required={!u || senha.length > 0}>
        <Input
          id="usuario-confirmar-senha"
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
