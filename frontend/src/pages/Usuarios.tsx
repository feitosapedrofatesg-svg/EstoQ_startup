import { useMemo, useState } from "react";
import { useAuth } from "../store/auth";
import { useFetch } from "../lib/hooks";
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
import { useToast } from "../store/toast";

const PERFIS: Perfil[] = ["ADMIN", "COZINHA", "NUTRICIONISTA"];

const PERFIL_LABEL: Record<Perfil, string> = {
  ADMIN: "Administrador",
  COZINHA: "Cozinha",
  NUTRICIONISTA: "Nutricionista",
  PLATAFORMA: "Plataforma",
};

const PERFIL_TONE: Record<Perfil, "neutral" | "accent" | "info" | "good"> = {
  ADMIN: "neutral",
  COZINHA: "accent",
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

  const { data, loading, error, refresh } = useFetch<Page<UsuarioResponseDTO>>(
    "/api/usuarios?size=100"
  );
  const users = data?.content ?? [];

  const rows = useMemo(() => {
    if (users.length === 0) return [];
    const q = search.trim().toLowerCase();
    return users.filter((u) => !q || u.nome.toLowerCase().includes(q) || u.email.toLowerCase().includes(q));
  }, [users, search]);

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
      toast.error("Não foi possível atualizar", (e as Error).message);
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
      toast.success("Usuário removido");
      void refresh();
    } catch (e) {
      toast.error("Não foi possível remover", (e as Error).message);
    } finally {
      setBusy("");
    }
  };

  return (
    <>
      <PageHeader
        title="Usuários"
        subtitle="Quem acessa o estoQ e o que cada perfil pode fazer."
        actions={
          <Button icon="plus" onClick={() => setModal({ open: true, u: null })}>
            Novo usuário
          </Button>
        }
      />

      <Card
        title="Equipe"
        actions={
          <Input
            type="search"
            placeholder="Buscar por nome ou e-mail…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Buscar usuário"
          />
        }
      >
        {error && <div className="alertbanner alertbanner--bad">{error}</div>}
        {loading && !data && <p className="muted">Carregando…</p>}
        {!loading && users.length === 0 ? (
          <EmptyState
            title="Nenhum usuário"
            text="Adicione os perfis que vão operar o sistema: administração, cozinha e nutrição."
          />
        ) : (
          <DataTable caption="Usuários do sistema" headers={["Nome", "E-mail", "Perfil", "Situação", "Criado em", ""]}>
            {rows.map((u) => (
              <tr key={u.id}>
                <td>
                  <strong>{u.nome}</strong>
                  {u.id === user?.id && <span className="cell-sub">é você</span>}
                </td>
                <td className="muted-cell">{u.email}</td>
                <td>
                  <StatusBadge tone={PERFIL_TONE[u.perfil]}>{PERFIL_LABEL[u.perfil]}</StatusBadge>
                </td>
                <td>
                  {u.ativo ? (
                    <StatusBadge label="Ativo" tone="good" />
                  ) : (
                    <StatusBadge label="Inativo" tone="neutral" />
                  )}
                </td>
                <td className="muted-cell">{fmtDateTime(u.dataHoraCriacao)}</td>
                <td>
                  <div className="td-actions">
                    <Button size="sm" variant="ghost" icon="edit" onClick={() => setModal({ open: true, u })}>
                      Editar
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={u.ativo ? "x" : "check"}
                      loading={busy === "toggle" + u.id}
                      onClick={() => void toggleAtivo(u)}
                      aria-label={u.ativo ? `Desativar ${u.nome}` : `Ativar ${u.nome}`}
                    />
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
        title="Remover usuário"
        message={`Desativar "${del?.nome}"? Ela/e não conseguirá mais entrar no sistema. O histórico fica preservado.`}
        danger
        confirmLabel="Desativar"
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
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (!nome.trim()) return setError("Informe o nome.");
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return setError("Informe um e-mail válido.");
    if (!u && senha.length < 8) return setError("A senha precisa ter ao menos 8 caracteres.");
    if (u && senha && senha.length < 8) return setError("A senha precisa ter ao menos 8 caracteres.");
    setSubmitting(true);
    setError(null);
    try {
      if (u) {
        await api.put(`/api/usuarios/${u.id}`, {
          version: u.version,
          nome: nome.trim(),
          email: email.trim().toLowerCase(),
          perfil,
          senha: senha || null,
          ativo: u.ativo,
        });
        toast.success("Usuário atualizado");
      } else {
        await api.post("/api/usuarios", {
          nome: nome.trim(),
          email: email.trim().toLowerCase(),
          senha,
          perfil,
        });
        toast.success("Usuário criado");
      }
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
      title={u ? `Editar ${u.nome}` : "Novo usuário"}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button onClick={() => void submit()} loading={submitting}>
            Salvar
          </Button>
        </>
      }
    >
      {error && (
        <div className="alertbanner alertbanner--bad" role="alert">
          {error}
        </div>
      )}
      <Field label="Nome" required>
        <Input value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Nome e sobrenome" />
      </Field>
      <Field label="E-mail" required>
        <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="pessoa@restaurante.com" />
      </Field>
      <Field label="Perfil" required>
        <Select value={perfil} onChange={(e) => setPerfil(e.target.value as Perfil)}>
          {PERFIS.map((p) => (
            <option key={p} value={p}>
              {PERFIL_LABEL[p]} — {p === "ADMIN" ? "tudo" : p === "COZINHA" ? "movimentações e estoque" : "CMV e relatórios"}
            </option>
          ))}
        </Select>
      </Field>
      <Field label={u ? "Nova senha (deixe vazio para manter)" : "Senha"} required={!u} hint="Ao menos 8 caracteres.">
        <Input
          type="password"
          autoComplete="new-password"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          placeholder={u ? "••••••••" : "Senha com 8+ caracteres"}
        />
      </Field>
    </Modal>
  );
}