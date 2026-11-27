import { traduzir } from "../lib/i18n";
import { Link } from "react-router-dom";
import { PaginaPublica } from "../components/PaginaPublica";

export function Sobre() {
  return <PaginaPublica>
    <h1>{" "}{traduzir("Estoque e CMV na rotina da sua cozinha.")}{" "}</h1>
    <p className="public-page__intro">{" "}{traduzir("O EstoQ é uma aplicação web para restaurantes acompanharem ingredientes, compras, consumo e desperdício. Sua equipe registra a operação e consulta os resultados no mesmo lugar.")}{" "}</p>
    <div className="public-page__actions">
      <Link className="btn btn--accent" to="/registro">{" "}{traduzir("Cadastrar minha cozinha")}{" "}</Link>
      <Link className="btn btn--ghost" to="/login">{" "}{traduzir("Já tenho conta")}{" "}</Link>
    </div>
    <section>
      <h2>{" "}{traduzir("Da entrada ao balanço")}{" "}</h2>
      <p>{" "}{traduzir("Registre entradas e notas fiscais, acompanhe lotes e validades, dê baixa no consumo ou no desperdício e confira o estoque pelo balanço físico.")}{" "}</p>
    </section>
    <section>
      <h2>{" "}{traduzir("Custos para decidir")}{" "}</h2>
      <p>{" "}{traduzir("Consulte o custo da mercadoria vendida (CMV), informe o faturamento do período e compare os resultados com a meta da cozinha. Os relatórios podem ser exportados para conferência.")}{" "}</p>
    </section>
    <section>
      <h2>{" "}{traduzir("Cada equipe com seu acesso")}{" "}</h2>
      <p>{" "}{traduzir("Administrador, Operacional e Nutricionista têm permissões próprias. Os dados são separados por restaurante. Você pode entrar com e-mail e senha ou, quando disponível, com sua conta Google.")}{" "}</p>
      <p>{" "}{traduzir("Para falar com o responsável pelo EstoQ, escreva para")}{" "}<a href="mailto:estoq.sistema@gmail.com">{" "}{traduzir("estoq.sistema@gmail.com")}{" "}</a>.</p>
    </section>
  </PaginaPublica>;
}
