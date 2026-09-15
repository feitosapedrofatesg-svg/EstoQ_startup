interface Bar {
  label: string;
  value: number;
  hint?: string;
}

export function BarChart({
  data,
  accent,
  unit,
  ariaLabel,
}: {
  data: Bar[];
  accent?: number[];
  unit?: string;
  ariaLabel: string;
}) {
  if (data.length === 0) return null;
  const w = 720;
  const h = 220;
  const top = 26;
  const bottom = 30;
  const max = Math.max(...data.map((d) => d.value), 1) * 1.12;
  const barW = Math.min(64, (w - 20) / data.length - 14);

  return (
    <figure className="chart" aria-label={ariaLabel} role="img">
      <svg viewBox={`0 0 ${w} ${h}`} className="chart__svg" aria-hidden="true">
        {[0.25, 0.5, 0.75, 1].map((t) => (
          <line
            key={t}
            x1="0"
            x2={w}
            y1={top + (h - top - bottom) * (1 - t)}
            y2={top + (h - top - bottom) * (1 - t)}
            className="chart__grid"
          />
        ))}
        {data.map((d, i) => {
          const bh = Math.max(2, ((h - top - bottom) * d.value) / max);
          const x = 12 + i * ((w - 24) / data.length) + ((w - 24) / data.length - barW) / 2;
          const y = h - bottom - bh;
          const isAccent = accent?.includes(i) ?? false;
          return (
            <g key={i}>
              <rect
                x={x}
                y={y}
                width={barW}
                height={bh}
                rx={Math.min(6, barW / 2)}
                fill="var(--ink)"
                className={isAccent ? "chart__bar chart__bar--accent" : "chart__bar"}
              />
              <text x={x + barW / 2} y={h - 10} textAnchor="middle" className="chart__label" fontSize="12">
                {d.label}
              </text>
              <title>{`${d.label}: ${d.value}${unit ? " " + unit : ""}${d.hint ? " (" + d.hint + ")" : ""}`}</title>
            </g>
          );
        })}
      </svg>
    </figure>
  );
}

export function HBars({
  rows,
  ariaLabel,
}: {
  rows: { label: string; sub?: string; value: number; money?: boolean }[];
  ariaLabel: string;
}) {
  if (rows.length === 0) return <p className="muted">Sem dados para exibir.</p>;
  const max = Math.max(...rows.map((r) => r.value), 1);
  return (
    <div className="hbars" role="img" aria-label={ariaLabel}>
      {rows.map((r, i) => (
        <div className="hbar" key={i}>
          <div className="hbar__head">
            <span className="hbar__label">{r.label}</span>
            <span className="hbar__value">{r.value}</span>
          </div>
          <div className="hbar__track">
            <div
              className="hbar__fill"
              style={{ width: `${Math.max(2, (r.value / max) * 100)}%` }}
            />
          </div>
          {r.sub && <p className="hbar__sub">{r.sub}</p>}
        </div>
      ))}
    </div>
  );
}

export function CmvGauge({
  percentual,
  ideal,
  label,
}: {
  percentual: number | null;
  ideal: number | null;
  label: string;
}) {
  const max = Math.max(ideal ?? 20, percentual ?? 20, 20) * 1.15;
  const pct = Math.min(100, ((percentual ?? 0) / max) * 100);
  const idealPct = Math.min(100, ((ideal ?? 0) / max) * 100);
  const within = percentual !== null && ideal !== null && percentual <= ideal;

  return (
    <div className="gauge">
      <div className="gauge__track" role="img" aria-label={`${label}: ${percentual ?? "—"}%, meta ${ideal ?? "—"}%`}>
        <div
          className={`gauge__fill ${within ? "gauge__fill--good" : "gauge__fill--bad"}`}
          style={{ width: `${pct}%` }}
        />
        {ideal !== null && (
          <div className="gauge__ideal" style={{ left: `${idealPct}%` }} aria-hidden="true" />
        )}
      </div>
      <div className="gauge__legend">
        <span>
          Atual: <strong>{percentual ? `${percentual.toLocaleString("pt-BR")}%` : "—"}</strong>
        </span>
        <span>
          Meta: <strong>{ideal ? `${ideal.toLocaleString("pt-BR")}%` : "—"}</strong>
        </span>
      </div>
    </div>
  );
}