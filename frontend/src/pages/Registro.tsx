import { traduzir } from "../lib/i18n";
import { ConfirmacoesCadastro, SEM_CONFIRMACAO } from "../components/ConfirmacoesCadastro";
import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Field, Input, Button } from "../components/UI";
import { api, ApiError } from "../lib/api";
import { Icon } from "../components/Icon";
import { useToast } from "../store/toast";
import { GoogleButton } from "../components/GoogleButton";
import { LinksPublicos } from "../components/PaginaPublica";

/** Auto-cadastro aberto: qualquer cozinha cria a própria conta na plataforma. */
export function Registro() {
  const navigate = useNavigate();
  const toast = useToast();
  const [confirmacoes,setConfirmacoes]=useState(SEM_CONFIRMACAO);
  const [nomeLoja, setNomeLoja] = useState("");
  const [nomeResponsavel, setNomeResponsavel] = useState("");
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [confirmarSenha, setConfirmarSenha] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!nomeLoja.trim() || !nomeResponsavel.trim() || !email.trim() || senha.length < 8) {
      setError(
        traduzir("Preencha todos os campos. A senha precisa ter ao menos 8 caracteres.")
      );
      return;
    }
    if (senha !== confirmarSenha) {
      setError(traduzir("As senhas não conferem."));
      return;
    }
    if(!confirmacoes.aceitouTermos||!confirmacoes.cientePrivacidade||!confirmacoes.versaoTermos){setError(traduzir("Leia os documentos e confirme os dois campos antes de cadastrar."));return;}
    setLoading(true);
    setError(null);
    try {
      const resposta = await api.post<{ confirmacaoEmailPendente?: boolean }>("/api/registro", {
        ...confirmacoes,
        nomeLoja: nomeLoja.trim(),
        nomeResponsavel: nomeResponsavel.trim(),
        email: email.trim().toLowerCase(),
        senha,
      });
      const pendente = resposta?.confirmacaoEmailPendente;
      toast.success(pendente ? "Conta criada! Confirme seu e-mail para entrar." : "Conta criada! Agora é só entrar.");
      navigate(pendente ? "/verificar-email" : "/login", { state: { email: email.trim().toLowerCase() } });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError(traduzir("Não foi possível criar a conta. Tente novamente."));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login">
      <div className="login__side">
        <div className="login__brand">
          <img
            className="login__logo"
            src="/logo_login.png"
            alt={traduzir("estoQ — controle de estoque e CMV para cozinhas")}
            width={260}
            height={173}
          />
        </div>
        <blockquote className="login__quote">
          <p className="login__quote-text">
            {" "}{traduzir("Cadastre a sua cozinha e comece a controlar estoque, CMV e desperdício em minutos.")}{" "}</p>
          <p className="login__quote-end">{" "}{traduzir("esto")}{" "}<span>{" "}{traduzir("Q")}{" "}</span> {" "}{traduzir("· cada cozinha com os seus dados")}{" "}</p>
        </blockquote>
      </div>

      <div className="login__panel">
        <form className="login__form" onSubmit={submit} noValidate>
          <h1 className="login__title">{" "}{traduzir("Criar conta")}{" "}</h1>
          <p className="login__subtitle">
            {" "}{traduzir("Primeiro acesso da sua cozinha. Você será o administrador.")}{" "}</p>

          {error && (
            <div className="login__error" role="alert">
              <Icon name="alert-circle" size={18} />
              {traduzir(error)}
            </div>
          )}

          <GoogleButton />

          <Field label={traduzir("Nome da cozinha")} htmlFor="reg-loja" required>
            <Input
              id="reg-loja"
              value={nomeLoja}
              onChange={(e) => setNomeLoja(e.target.value)}
              placeholder={traduzir("Ex.: Cantina da Esquina")}
            />
          </Field>

          <Field label={traduzir("Seu nome")} htmlFor="reg-nome" required>
            <Input
              id="reg-nome"
              value={nomeResponsavel}
              onChange={(e) => setNomeResponsavel(e.target.value)}
              placeholder={traduzir("Nome e sobrenome")}
            />
          </Field>

          <Field label={traduzir("E-mail de acesso")} htmlFor="reg-email" required>
            <Input
              id="reg-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={traduzir("voce@restaurante.com")}
            />
          </Field>

          <Field label={traduzir("Senha")} htmlFor="reg-senha" required hint={traduzir("Ao menos 8 caracteres.")}>
            <Input
              id="reg-senha"
              type="password"
              autoComplete="new-password"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              placeholder={traduzir("Senha com 8+ caracteres")}
            />
          </Field>

          <Field label={traduzir("Confirmar senha")} htmlFor="reg-confirmar-senha" required>
            <Input
              id="reg-confirmar-senha"
              type="password"
              autoComplete="new-password"
              value={confirmarSenha}
              onChange={(e) => setConfirmarSenha(e.target.value)}
              placeholder={traduzir("Repita a senha")}
            />
          </Field>

          <ConfirmacoesCadastro valor={confirmacoes} onChange={setConfirmacoes} />
          <Button type="submit" disabled={!confirmacoes.aceitouTermos||!confirmacoes.cientePrivacidade||!confirmacoes.versaoTermos} block size="lg" loading={loading} className="login__submit">
            {" "}{traduzir("Criar conta")}{" "}</Button>

          <p className="login__signup">
            {" "}{traduzir("Já tem conta?")}{" "}
            <Link to="/login" className="login__signup-link">
              {" "}{traduzir("Entrar")}{" "}</Link>
          </p>
        </form>
        <p className="login__foot">
          {" "}{traduzir("Os dados da sua cozinha ficam isolados: só quem a sua equipe acessa é você.")}{" "}</p>
        <LinksPublicos />
      </div>
    </div>
  );
}
