import { Link } from "react-router-dom";
import { PaginaPublica } from "../components/PaginaPublica";

export function Sobre() {
  return <PaginaPublica>
    <h1>Estoque e CMV na rotina da sua cozinha.</h1>
    <p className="public-page__intro">O EstoQ é uma aplicação web para restaurantes acompanharem
      ingredientes, compras, consumo e desperdício. Sua equipe registra a operação e consulta os resultados no mesmo lugar.</p>
    <div className="public-page__actions">
      <Link className="btn btn--accent" to="/registro">Cadastrar minha cozinha</Link>
      <Link className="btn btn--ghost" to="/login">Já tenho conta</Link>
    </div>
    <section>
      <h2>Da entrada ao balanço</h2>
      <p>Registre entradas e notas fiscais, acompanhe lotes e validades, dê baixa no consumo
        ou no desperdício e confira o estoque pelo balanço físico.</p>
    </section>
    <section>
      <h2>Custos para decidir</h2>
      <p>Consulte o custo da mercadoria vendida (CMV), informe o faturamento do período e
        compare os resultados com a meta da cozinha. Os relatórios podem ser exportados para conferência.</p>
    </section>
    <section>
      <h2>Cada equipe com seu acesso</h2>
      <p>Administrador, Operacional e Nutricionista têm permissões próprias.
        Os dados são separados por restaurante. Você pode entrar com e-mail e senha ou,
        quando disponível, com sua conta Google.</p>
      <p>Para falar com o responsável pelo EstoQ, escreva para <a href="mailto:estoq.sistema@gmail.com">estoq.sistema@gmail.com</a>.</p>
    </section>
  </PaginaPublica>;
}
