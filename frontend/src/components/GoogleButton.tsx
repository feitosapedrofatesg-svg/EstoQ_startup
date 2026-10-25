import { useEffect, useState } from "react";
import { api } from "../lib/api";

export function GoogleButton() {
  const [ativo, setAtivo] = useState(false);
  useEffect(() => {
    let montado = true;
    void api.get<{ enabled: boolean }>("/api/auth/google/configuracao")
      .then(config => { if (montado) setAtivo(config.enabled === true); })
      .catch(() => { /* O acesso com e-mail e senha continua disponível. */ });
    return () => { montado = false; };
  }, []);

  if (!ativo) return null;
  return <div className="login__google">
    <a className="google-button" href="/api/oauth2/authorization/google">
      <img src="/google-g.png" width={20} height={20} alt="" />
      Continuar com Google
    </a>
    <div className="login__divider"><span>ou use seu e-mail</span></div>
  </div>;
}
