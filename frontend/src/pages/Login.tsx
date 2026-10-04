import { useState, type FormEvent } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../store/auth";
import { Field, Input, Button } from "../components/UI";
import { ApiError } from "../lib/api";
import { Icon } from "../components/Icon";

export function Login() {
  const { login } = useAuth();
  const location = useLocation();
  const prefilled = (location.state as { email?: string } | null)?.email ?? "";
  const [email, setEmail] = useState(prefilled);
  const [senha, setSenha] = useState("");
  const [senhaVisivel, setSenhaVisivel] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !senha) {
      setError("Informe o e-mail e a senha para entrar.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await login(email.trim().toLowerCase(), senha);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 429) {
          setError("Muitas tentativas de acesso. Espere alguns minutos e tente novamente.");
        } else if (err.status === 401) {
          setError("E-mail ou senha incorretos.");
        } else {
          setError(err.message);
        }
      } else {
        setError("Não foi possível entrar. Tente novamente.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login login--page">
      <div className="login__side">
        <video
          className="login__video"
          src="/login-kitchen.mp4"
          autoPlay
          muted
          loop
          playsInline
          preload="metadata"
          aria-hidden="true"
          tabIndex={-1}
        />
        <div className="login__message">
          <p className="login__message-title">Menos desperdício, mais lucro.</p>
          <p className="login__message-copy">O estoque e o CMV da sua cozinha em um só lugar.</p>
          <p className="login__message-brand">estoQ · feito para quem cozinha de verdade</p>
        </div>
      </div>

      <div className="login__panel">
        <form className="login__form" onSubmit={submit} noValidate>
          <h1 className="login__title">Entrar</h1>
          <p className="login__subtitle">
            Use o cadastro da sua equipe. Cada cozinha acessa com seu próprio perfil.
          </p>

          {error && (
            <div className="login__error" role="alert">
              <Icon name="alert-circle" size={18} />
              {error}
            </div>
          )}

          <Field label="E-mail" htmlFor="login-email" required>
            <Input
              id="login-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="voce@restaurante.com"
            />
          </Field>

          <Field label="Senha" htmlFor="login-senha" required>
            <div className="login__password-wrap">
              <Input
                id="login-senha"
                type={senhaVisivel ? "text" : "password"}
                autoComplete="current-password"
                value={senha}
                onChange={(e) => setSenha(e.target.value)}
                placeholder="Sua senha de acesso"
              />
              <button
                type="button"
                className="login__password-toggle"
                aria-label={senhaVisivel ? "Ocultar senha" : "Mostrar senha"}
                aria-pressed={senhaVisivel}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => setSenhaVisivel((visivel) => !visivel)}
              >
                <Icon name={senhaVisivel ? "eye-off" : "eye"} size={19} />
              </button>
            </div>
          </Field>

          <Button type="submit" block size="lg" loading={loading} className="login__submit">
            Entrar
          </Button>

          <p className="login__signup">
            Sua cozinha ainda não usa o estoQ?{" "}
            <Link to="/registro" className="login__signup-link">
              Crie a conta agora
            </Link>
          </p>
        </form>
        <p className="login__foot">
          Problemas para entrar? Fale com o administrador da cozinha.
        </p>
      </div>
    </div>
  );
}