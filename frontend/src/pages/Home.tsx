import { useState } from "react";
import { Link } from "react-router-dom";
import { CabecalhoPublico, RodapePublico } from "../components/PaginaPublica";
import { Carrossel, type CarrosselSlide } from "../components/Carrossel";
import { Icon } from "../components/Icon";

const RAZAO = [
  {
    nome: "Estoque",
    resumo: "Produtos e saldos",
    icone: "boxes",
    detalhe: "Entradas por nota fiscal ou direta, lote e validade do fabricante, saldo por produto e filtro do que já está abaixo do mínimo.",
  },
  {
    nome: "Consumo",
    resumo: "O que a cozinha usou",
    icone: "check-circle",
    detalhe: "Busca pelo produto, escolha da embalagem e registro por quem opera a tela. O saldo cai na hora.",
  },
  {
    nome: "Desperdício",
    resumo: "Perda registrada à parte",
    icone: "trash",
    detalhe: "A perda fica separada do consumo para ver quanto saiu do controle no período.",
  },
  {
    nome: "Balanço físico",
    resumo: "Sistema x geladeira",
    icone: "scale",
    detalhe: "Contagem por produto comparada com o saldo registrado. O painel mostra o próximo balanço.",
  },
  {
    nome: "Relatórios e CMV",
    resumo: "Custo da mercadoria vendida",
    icone: "chart",
    detalhe: "CMV do período, faturamento por dia, comparação com a meta e exportação para conferência.",
  },
  {
    nome: "Usuários",
    resumo: "Três perfis de acesso",
    icone: "users",
    detalhe: "Administrador, Operacional e Nutricionista com permissões próprias. Os dados ficam separados por restaurante.",
  },
];

const CENARIOS = [
  {
    foto: "/fotos/caixas.jpg",
    titulo: "O que chegou vira estoque",
    texto: "A entrada por nota ou direta carrega quantidade, fornecedor, lote e validade.",
  },
  {
    foto: "/fotos/cozinha.jpg",
    titulo: "A cozinha registra o que usa",
    texto: "Consumo e desperdício entram na hora, por quem está operando o serviço.",
  },
  {
    foto: "/fotos/insumos.jpg",
    titulo: "O que vence sai antes",
    texto: "Saldo e validade acompanham o que está guardado, sem planilha lateral.",
  },
];

/** Telas reais do sistema, na ordem do carrossel do topo. */
const TELAS: CarrosselSlide[] = [
  {
    src: "/print/catalogo.jpg",
    titulo: "Catálogo",
    alt: "Catálogo do EstoQ com produtos, categorias, unidade de medida e saldo de cada item.",
    width: 1600, height: 775,
  },
  {
    src: "/print/movimentacoes.jpg",
    titulo: "Movimentações",
    alt: "Movimentações do EstoQ com compras, consumo e desperdício registrados no período.",
    width: 1600, height: 774,
  },
  {
    src: "/print/desperdicio.jpg",
    titulo: "Desperdício",
    alt: "Histórico de desperdício do EstoQ com motivo da perda, quantidade e prejuízo.",
    width: 1600, height: 768,
  },
  {
    src: "/print/balanco.jpg",
    titulo: "Balanço físico",
    alt: "Balanço físico do EstoQ com agendamento semanal e contagem já concluída.",
    width: 1600, height: 770,
  },
  {
    src: "/print/relatorios-cmv.jpg",
    titulo: "Relatórios e CMV",
    alt: "Relatórios e CMV do EstoQ com custo do período, meta e faturamento por dia.",
    width: 1600, height: 770,
  },
  {
    src: "/print/alertas.jpg",
    titulo: "Central de alertas",
    alt: "Central de alertas do EstoQ aberta sobre o painel, com balanço pendente e estoque baixo.",
    width: 1600, height: 772,
  },
];

export function Home() {
  const [aberta, setAberta] = useState<string | null>(null);
  const [faturamento, setFaturamento] = useState(100000);

  const moeda = (v: number) =>
    new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 0 }).format(v);
  const ponto = Math.round(faturamento * 0.01);

  return (
    <div className="home">
      <div className="home__barra">
        <CabecalhoPublico />
      </div>

      <main>
      <section className="home-hero">
        <img className="home-hero__foto" src="/fotos/passe.jpg" alt="" aria-hidden="true" width={1500} height={1000} />
        <div className="home-hero__veu" aria-hidden="true" />
        <div className="home-hero__conteudo">
          <h1>Menos desperdício, mais lucro.</h1>
          <p>
            O EstoQ mostra o que entra, o que sai e o que sobra na sua cozinha:
            estoque, validade, CMV e balanço no mesmo lugar.
          </p>
          <div className="public-page__actions home-hero__acoes">
            <Link className="btn btn--accent btn--lg" to="/login">Entrar</Link>
            <Link className="btn btn--outline btn--lg" to="/registro">Cadastrar minha cozinha</Link>
          </div>
          <p className="home-hero__nota">Entre com e-mail e senha ou com sua conta Google.</p>
        </div>
      </section>

      <div className="home__painel">
        <figure className="home-painel">
          <Carrossel slides={TELAS} rotulo="Telas do EstoQ" intervalo={5000} />
          <figcaption>Telas do EstoQ: catálogo, movimentações, desperdício, balanço, relatórios de CMV e alertas.</figcaption>
        </figure>
      </div>

      <section className="home-razao" aria-labelledby="titulo-razao">
        <div className="home__barra">
          <h2 id="titulo-razao">O que você encontra dentro</h2>
          <ul className="razao">
            {RAZAO.map((item) => {
              const aberto = aberta === item.nome;
              const id = `razao-${item.nome.replace(/\s+/g, "-").toLowerCase()}`;
              return (
                <li key={item.nome} className="razao__linha">
                  <h3 className="razao__titulo">
                    <button
                      type="button"
                      className="razao__botao"
                      aria-expanded={aberto}
                      aria-controls={id}
                      onClick={() => setAberta(aberto ? null : item.nome)}
                    >
                      <span className="razao__icone" aria-hidden="true">
                        <Icon name={item.icone} size={20} />
                      </span>
                      <span className="razao__nome">{item.nome}</span>
                      <span className="razao__resumo">{item.resumo}</span>
                      <span className="razao__sinal" aria-hidden="true">{aberto ? "−" : "+"}</span>
                    </button>
                  </h3>
                  <div className="razao__detalhe" id={id} hidden={!aberto}>
                    <p>{item.detalhe}</p>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      </section>

      <section className="home-cenarios" aria-labelledby="titulo-cenarios">
        <div className="home__barra">
          <h2 id="titulo-cenarios">Do caminhão à geladeira</h2>
          <ul className="cenario">
            {CENARIOS.map((c) => (
              <li key={c.titulo} className="cenario__item">
                <img src={c.foto} alt="" aria-hidden="true" width={900} height={700} loading="lazy" />
                <div className="cenario__texto">
                  <h3>{c.titulo}</h3>
                  <p>{c.texto}</p>
                </div>
              </li>
            ))}
          </ul>
          <p className="home__creditos">Fotos de cozinhas e bancas: Rawpixel e StockSnap, domínio público (CC0).</p>
        </div>
      </section>

      <section className="home-ponto" aria-labelledby="titulo-ponto">
        <div className="home__barra home-ponto__grid">
          <div className="home-ponto__conta">
            <h2 id="titulo-ponto">Quanto vale um ponto de CMV</h2>
            <p>
              CMV é o custo da mercadoria vendida. Cada ponto percentual que você
              reduz equivale a 1% do faturamento do mês.
            </p>
            <label className="home-ponto__rotulo" htmlFor="faturamento">Faturamento mensal</label>
            <input
              className="home-ponto__controle"
              id="faturamento"
              type="range"
              min={10000}
              max={500000}
              step={5000}
              value={faturamento}
              onChange={(e) => setFaturamento(Number(e.target.value))}
            />
            <output className="home-ponto__faturamento" htmlFor="faturamento">{moeda(faturamento)}</output>
          </div>
          <div className="home-ponto__resultado">
            <p className="home-ponto__rotulo">1 ponto percentual de CMV</p>
            <p className="home-ponto__numero">{moeda(ponto)}</p>
            <p className="home-ponto__periodo">por mês · {moeda(ponto * 12)} por ano</p>
          </div>
        </div>
        <div className="home__barra">
          <figure className="home-ponto__print">
            <img src="/print/cmv-painel.jpg" width={1600} height={772}
              alt="Painel do EstoQ com o CMV do período, a comparação com a meta e os alertas do dia"
              loading="lazy" decoding="async" />
          </figure>
        </div>
        <div className="home__barra">
          <p className="home__creditos">
            A conta é faturamento × 1%. Para virar economia de verdade, o que deixou
            de ir ao lixo precisa ser aproveitado ou reduzir as compras de amanhã.
          </p>
        </div>
      </section>

      <section className="home-fecho">
        <div className="home__barra home-fecho__conteudo">
          <p className="home-fecho__frase">Abra a sua cozinha no EstoQ.</p>
          <div className="public-page__actions">
            <Link className="btn btn--accent btn--lg" to="/login">Entrar</Link>
            <Link className="btn btn--outline btn--lg" to="/registro">Cadastrar minha cozinha</Link>
          </div>
          <p className="home-fecho__nota">
            Dúvidas fale com <a href="mailto:estoq.sistema@gmail.com">estoq.sistema@gmail.com</a>.
          </p>
        </div>
      </section>
      </main>

      <div className="home__barra home__rodape">
        <RodapePublico />
      </div>
    </div>
  );
}
