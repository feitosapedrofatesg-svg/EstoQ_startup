import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Field, Input, Button } from "../components/UI";
import { api, ApiError } from "../lib/api";
import { Icon } from "../components/Icon";
import { useToast } from "../store/toast";

/** Auto-cadastro aberto: qualquer cozinha cria a própria conta na plataforma. */
export function Registro() {
  const navigate = useNavigate();
  const toast = useToast();
  const [nomeLoja, setNomeLoja] = useState("");
  const [nomeResponsavel, setNomeResponsavel] = useState("");
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!nomeLoja.trim() || !nomeResponsavel.trim() || !email.trim() || senha.length < 8) {
      setError(
        "Preencha todos os campos. A senha precisa ter ao menos 8 caracteres."
      );
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await api.post("/api/registro", {
        nomeLoja: nomeLoja.trim(),
        nomeResponsavel: nomeResponsavel.trim(),
        email: email.trim().toLowerCase(),
        senha,
      });
      toast.success("Conta criada! Agora é só entrar.");
      navigate("/login", { state: { email: email.trim().toLowerCase() } });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Não foi possível criar a conta. Tente novamente.");
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
            alt="estoQ — controle de estoque e CMV para cozinhas"
            width={260}
            height={173}
          />
        </div>
        <blockquote className="login__quote">
          <p className="login__quote-text">
            Cadastre a sua cozinha e comece a controlar estoque, CMV e desperdício em minutos.
          </p>
          <p className="login__quote-end">esto<span>Q</span> · cada cozinha com os seus dados</p>
        </blockquote>
      </div>

      <div className="login__panel">
        <form className="login__form" onSubmit={submit} noValidate>
          <h1 className="login__title">Criar conta</h1>
          <p className="login__subtitle">
            Primeiro acesso da sua cozinha. Você será o administrador.
          </p>

          {error && (
            <div className="login__error" role="alert">
              <Icon name="alert-circle" size={18} />
              {error}
            </div>
          )}

          <Field label="Nome da cozinha" htmlFor="reg-loja" required>
            <Input
              id="reg-loja"
              value={nomeLoja}
              onChange={(e) => setNomeLoja(e.target.value)}
              placeholder="Ex.: Cantina da Esquina"
            />
          </Field>

          <Field label="Seu nome" htmlFor="reg-nome" required>
            <Input
              id="reg-nome"
              value={nomeResponsavel}
              onChange={(e) => setNomeResponsavel(e.target.value)}
              placeholder="Nome e sobrenome"
            />
          </Field>

          <Field label="E-mail de acesso" htmlFor="reg-email" required>
            <Input
              id="reg-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="voce@restaurante.com"
            />
          </Field>

          <Field label="Senha" htmlFor="reg-senha" required hint="Ao menos 8 caracteres.">
            <Input
              id="reg-senha"
              type="password"
              autoComplete="new-password"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              placeholder="Senha com 8+ caracteres"
            />
          </Field>

          <Button type="submit" block size="lg" loading={loading} className="login__submit">
            Criar conta
          </Button>

          <p className="login__signup">
            Já tem conta?{" "}
            <Link to="/login" className="login__signup-link">
              Entrar
            </Link>
          </p>
        </form>
        <p className="login__foot">
          Os dados da sua cozinha ficam isolados: só quem a sua equipe acessa é você.
        </p>
      </div>
    </div>
  );
}