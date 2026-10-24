import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { Button, Field, Input } from "../components/UI";
import { api, mensagemErro } from "../lib/api";

function PaginaEmail({ titulo, children }: { titulo: string; children: ReactNode }) {
  return <div className="login">
    <div className="login__side">
      <div className="login__brand"><img className="login__logo" src="/logo_login.png" alt="estoQ" width={260} height={173} /></div>
      <blockquote className="login__quote"><p className="login__quote-text">O acesso da sua equipe começa por um e-mail confirmado.</p></blockquote>
    </div>
    <div className="login__panel"><div className="login__form">
      <h1 className="login__title" style={{ fontSize: "clamp(1.5rem, 6vw, 2rem)" }}>{titulo}</h1>
      {children}
      <p className="login__signup"><Link to="/login" className="login__signup-link">Voltar para entrar</Link></p>
    </div></div>
  </div>;
}

export function ConfirmarEmail() {
  const [token, setToken] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "");
  const [confirmado, setConfirmado] = useState(false);
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState("");
  useEffect(() => {
    // Remove o token da barra; não o envia em URL de API nem o salva no navegador.
    window.history.replaceState(window.history.state, "", window.location.pathname);
  }, []);

  async function confirmar() {
    setLoading(true); setErro("");
    try {
      await api.post("/api/auth/email/confirmar", { token });
      setConfirmado(true); setToken("");
    } catch (e) { setErro(mensagemErro(e)); }
    finally { setLoading(false); }
  }

  return <PaginaEmail titulo={confirmado ? "E-mail confirmado" : "Confirmar e-mail"}>
    {confirmado ? <p role="status">Seu e-mail foi confirmado. Agora você pode entrar com sua senha.</p> : <>
      <p className="login__subtitle">Pressione o botão para confirmar este endereço. O link vale por 24 horas e pode ser usado uma vez.</p>
      {erro && <div className="login__error" role="alert">{erro}</div>}
      {token ? <Button block onClick={confirmar} loading={loading}>Confirmar e-mail</Button>
        : <p role="alert">Abra o link recebido por e-mail ou solicite outro abaixo.</p>}
      <p className="login__signup"><Link className="login__signup-link" to="/verificar-email">Solicitar outro link</Link></p>
    </>}
  </PaginaEmail>;
}

export function VerificarEmail() {
  const location = useLocation();
  const [email, setEmail] = useState((location.state as { email?: string } | null)?.email ?? "");
  const [loading, setLoading] = useState(false);
  const [aguardar, setAguardar] = useState(false);
  const [mensagem, setMensagem] = useState("");
  const [erro, setErro] = useState("");
  useEffect(() => {
    if (!aguardar) return;
    const timer = window.setTimeout(() => setAguardar(false), 60_000);
    return () => window.clearTimeout(timer);
  }, [aguardar]);

  async function reenviar(e: FormEvent) {
    e.preventDefault(); setLoading(true); setErro(""); setMensagem("");
    try {
      const resposta = await api.post<{ message: string }>("/api/auth/email/reenviar", { email: email.trim().toLowerCase() });
      setMensagem(resposta.message); setAguardar(true);
    } catch (e) { setErro(mensagemErro(e)); }
    finally { setLoading(false); }
  }

  return <PaginaEmail titulo="Confira seu e-mail">
    <p className="login__subtitle">Para entrar, confirme o endereço pelo link recebido. Confira também o spam. Se precisar de outro link, informe o e-mail abaixo.</p>
    <form onSubmit={reenviar}>
      {erro && <div className="login__error" role="alert">{erro}</div>}
      {mensagem && <p role="status">{mensagem}</p>}
      <Field label="E-mail" htmlFor="confirmacao-email" required>
        <Input id="confirmacao-email" type="email" autoComplete="email" maxLength={254} required value={email} onChange={e => setEmail(e.target.value)} />
      </Field>
      <Button type="submit" block loading={loading} disabled={aguardar}>{aguardar ? "Aguarde 1 minuto para reenviar" : "Reenviar link de confirmação"}</Button>
    </form>
  </PaginaEmail>;
}
