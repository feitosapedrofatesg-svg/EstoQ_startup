import { useCallback, useEffect, useState, type KeyboardEvent } from "react";

export type CarrosselSlide = {
  src: string;
  /** Título curto, usado no rótulo de cada ponto. */
  titulo: string;
  alt: string;
  width: number;
  height: number;
};

type Props = {
  slides: CarrosselSlide[];
  /** Nome do carrossel para leitores de tela. */
  rotulo: string;
  /** Pausa entre telas, em milissegundos. */
  intervalo?: number;
};

/**
 * Carrossel de imagens com avanço automático, setas, pontos e teclado.
 * Sem dependência externa: o movimento é resolvido em CSS.
 * Com `prefers-reduced-motion` o avanço automático é desligado.
 */
export function Carrossel({ slides, rotulo, intervalo = 5000 }: Props) {
  const total = slides.length;
  const [ativo, setAtivo] = useState(0);
  const [pausado, setPausado] = useState(false);
  const [parado, setParado] = useState(false);

  // A preferência pode mudar enquanto a página está aberta.
  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setParado(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);

  const irPara = useCallback((indice: number) => {
    setAtivo(((indice % total) + total) % total);
  }, [total]);

  useEffect(() => {
    if (pausado || parado || total < 2) return;
    const id = window.setInterval(() => setAtivo((i) => (i + 1) % total), intervalo);
    return () => window.clearInterval(id);
  }, [pausado, parado, total, intervalo]);

  const teclado = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key === "ArrowLeft") { evento.preventDefault(); irPara(ativo - 1); }
    if (evento.key === "ArrowRight") { evento.preventDefault(); irPara(ativo + 1); }
  };

  return (
    <div
      className="carrossel"
      role="group"
      aria-roledescription="carrossel"
      aria-label={rotulo}
      onMouseEnter={() => setPausado(true)}
      onMouseLeave={() => setPausado(false)}
      onFocus={() => setPausado(true)}
      onBlur={(evento) => {
        if (!evento.currentTarget.contains(evento.relatedTarget as Node | null)) setPausado(false);
      }}
      onKeyDown={teclado}
    >
      <div className="carrossel__pistas">
        {slides.map((slide, indice) => {
          const visivel = indice === ativo;
          return (
            <div className="carrossel__pista" key={slide.src} data-ativo={visivel || undefined}
                 aria-hidden={!visivel}>
              <img
                src={slide.src}
                alt={visivel ? slide.alt : ""}
                width={slide.width}
                height={slide.height}
                loading={indice === 0 ? "eager" : "lazy"}
                decoding="async"
                draggable={false}
              />
            </div>
          );
        })}
      </div>

      {total > 1 && (
        <>
          <button type="button" className="carrossel__seta carrossel__seta--antes"
                  aria-label="Tela anterior" onClick={() => irPara(ativo - 1)}>
            <span aria-hidden="true">‹</span>
          </button>
          <button type="button" className="carrossel__seta carrossel__seta--depois"
                  aria-label="Próxima tela" onClick={() => irPara(ativo + 1)}>
            <span aria-hidden="true">›</span>
          </button>

          <div className="carrossel__pontos">
            {slides.map((slide, indice) => (
              <button
                type="button"
                key={slide.src}
                className="carrossel__ponto"
                aria-label={`Tela ${indice + 1} de ${total}: ${slide.titulo}`}
                aria-current={indice === ativo ? "true" : undefined}
                onClick={() => irPara(indice)}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
