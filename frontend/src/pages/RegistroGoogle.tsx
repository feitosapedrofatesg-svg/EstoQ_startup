import { traduzir } from "../lib/i18n";
import { ConfirmacoesCadastro, SEM_CONFIRMACAO } from "../components/ConfirmacoesCadastro";
import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button, Field, Input } from "../components/UI";
import { api, mensagemErro } from "../lib/api";
import type { AuthenticatedUserDTO } from "../lib/types";
import { useAuth } from "../store/auth";

interface PendenteGoogle {
  etapa: "CADASTRO" | "VINCULAR" | "CONFIRMAR_EMAIL";
  nome: string;
  email: string;
}
interface ResultadoGoogle {
  usuario: AuthenticatedUserDTO | null;
  confirmacaoEmailPendente: boolean;
  email: string;
}

export function RegistroGoogle() {
  const navigate = useNavigate();
  const { concluirLogin } = useAuth();
  const [confirmacoes,setConfirmacoes]=useState(SEM_CONFIRMACAO);
  const [pendente, setPendente] = useState<PendenteGoogle | null>(null);
  const [nomeLoja, setNomeLoja] = useState("");
  const [nomeResponsavel, setNomeResponsavel] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState("");
  const [loading, setLoading] = useState(false);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    let montado = true;
    void api.get<PendenteGoogle>("/api/auth/google/pendente").then(p => {
      if (!montado) return;
      if (p.etapa === "CONFIRMAR_EMAIL") {
        navigate("/verificar-email", { replace: true, state: { email: p.email } });
        return;
      }
      setPendente(p);
      setNomeResponsavel(p.nome);
    }).catch(e => { if (montado) setErro(mensagemErro(e)); })
      .finally(() => { if (montado) setCarregando(false); });
    return () => { montado = false; };
  }, [navigate]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!pendente) return;
    const cadastro = pendente.etapa === "CADASTRO";
    if (cadastro && (!nomeLoja.trim() || !nomeResponsavel.trim())) {
      setErro(traduzir("Informe o nome da cozinha e o seu nome.")); return;
    }
    if (!cadastro && !senha) { setErro(traduzir("Informe sua senha atual do EstoQ para vincular a conta.")); return; }
    if(cadastro&&(!confirmacoes.aceitouTermos||!confirmacoes.cientePrivacidade||!confirmacoes.versaoTermos)){setErro(traduzir("Leia os documentos e confirme os dois campos antes de cadastrar."));return;}
    setLoading(true); setErro("");
    try {
      const resposta = await api.post<ResultadoGoogle>(cadastro ? "/api/registro/google" : "/api/auth/google/vincular",
        cadastro ? { ...confirmacoes, nomeLoja: nomeLoja.trim(), nomeResponsavel: nomeResponsavel.trim() } : { senha });
      setSenha("");
      if (resposta.confirmacaoEmailPendente) {
        navigate("/verificar-email", { replace: true, state: { email: resposta.email } });
      } else if (resposta.usuario) {
        concluirLogin(resposta.usuario);
        navigate("/", { replace: true });
      }
    } catch (e) { setErro(mensagemErro(e)); }
    finally { setLoading(false); }
  }

  async function cancelar() {
    setLoading(true); setErro("");
    try {
      await api.post("/api/auth/google/cancelar");
      navigate("/login", { replace: true });
    } catch (e) { setErro(mensagemErro(e)); setLoading(false); }
  }

  const cadastro = pendente?.etapa === "CADASTRO";
  return <div className="login">
    <div className="login__side">
      <div className="login__brand"><img className="login__logo" src="/logo_login.png" alt={traduzir("estoQ")} width={260} height={173} /></div>
      <blockquote className="login__quote"><p className="login__quote-text">{" "}{traduzir("Sua cozinha com estoque, CMV e desperdício em um só lugar.")}{" "}</p></blockquote>
    </div>
    <div className="login__panel"><form className="login__form" onSubmit={submit} noValidate>
      <h1 className="login__title">{carregando ? traduzir("Aguarde…") : pendente ? cadastro ? traduzir("Concluir cadastro") : traduzir("Vincular conta Google") : traduzir("Acesso com Google")}</h1>
      {erro && <div className="login__error" role="alert">{traduzir(erro)}</div>}
      {pendente && <>
        <p className="login__subtitle">{cadastro
          ? traduzir("Seu e-mail veio do Google. Falta só informar sua cozinha. Você será o administrador.")
          : traduzir("Já existe uma conta EstoQ com este e-mail. Confirme sua senha atual para vincular o Google. Seus dados e seu perfil serão mantidos.")}</p>
        <Field label={traduzir("E-mail")} htmlFor="google-email">
          <Input id="google-email" type="email" value={pendente.email} readOnly />
        </Field>
        {cadastro ? <>
          <Field label={traduzir("Nome da cozinha")} htmlFor="google-loja" required>
            <Input id="google-loja" maxLength={120} autoComplete="organization" value={nomeLoja} onChange={e => setNomeLoja(e.target.value)} />
          </Field>
          <Field label={traduzir("Seu nome")} htmlFor="google-nome" required>
            <Input id="google-nome" maxLength={120} autoComplete="name" value={nomeResponsavel} onChange={e => setNomeResponsavel(e.target.value)} />
          </Field>
        </> : <Field label={traduzir("Senha atual do EstoQ")} htmlFor="google-senha" required>
          <Input id="google-senha" type="password" autoComplete="current-password" maxLength={72} value={senha} onChange={e => setSenha(e.target.value)} />
        </Field>}
        {cadastro&&<ConfirmacoesCadastro valor={confirmacoes} onChange={setConfirmacoes} />}
        <Button block type="submit" disabled={cadastro&&(!confirmacoes.aceitouTermos||!confirmacoes.cientePrivacidade||!confirmacoes.versaoTermos)} loading={loading}>{cadastro ? traduzir("Criar minha cozinha") : traduzir("Vincular e entrar")}</Button>
        <Button block variant="ghost" type="button" disabled={loading} onClick={() => void cancelar()}>{" "}{traduzir("Cancelar")}{" "}</Button>
      </>}
      {!carregando && !pendente && <p className="login__signup"><Link className="login__signup-link" to="/login">{" "}{traduzir("Voltar e tentar novamente")}{" "}</Link></p>}
    </form></div>
  </div>;
}
