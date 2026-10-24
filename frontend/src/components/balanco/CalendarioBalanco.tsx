import { useState } from "react";



import { Button } from "../UI";

import type { PeriodicidadeBalanco } from "../../lib/types";






function buildMonthGrid(year: number, month: number) {
  const first = new Date(year, month, 1);
  let startDow = first.getDay();
  startDow = startDow === 0 ? 6 : startDow - 1;
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells: { day: number; current: boolean; date: Date }[] = [];
  const prevMonthDays = new Date(year, month, 0).getDate();
  for (let i = startDow - 1; i >= 0; i--) {
    cells.push({ day: prevMonthDays - i, current: false, date: new Date(year, month - 1, prevMonthDays - i) });
  }
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push({ day: d, current: true, date: new Date(year, month, d) });
  }
  while (cells.length % 7 !== 0) {
    const d = cells.length - (startDow + daysInMonth) + 1;
    cells.push({ day: d, current: false, date: new Date(year, month + 1, d) });
  }
  return cells;
}

function MonthCalendar({
  value,
  onChange,
}: {
  value: number | null;
  onChange: (v: number) => void;
}) {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth());

  const cells = buildMonthGrid(viewYear, viewMonth);

  const go = (dir: number) => {
    setViewMonth((m) => {
      const next = m + dir;
      if (next < 0) { setViewYear((y) => y - 1); return 11; }
      if (next > 11) { setViewYear((y) => y + 1); return 0; }
      return next;
    });
  };

  return (
    <div className="month-cal">
      <header className="month-cal__nav">
        <Button type="button" variant="ghost" size="sm" icon="chevron-left" onClick={() => go(-1)} aria-label="Mês anterior" />
        <span className="month-cal__title">
          {MONTH_NAMES[viewMonth]} {viewYear}
        </span>
        <Button type="button" variant="ghost" size="sm" icon="chevron-right" onClick={() => go(1)} aria-label="Próximo mês" />
      </header>
      <div className="month-cal__weekdays" role="row">
        {WEEKDAY_LABELS.map((w) => (
          <span key={w} className="month-cal__weekday">{w}</span>
        ))}
      </div>
      <div className="month-cal__grid" role="grid">
        {cells.map((c, i) => {
          const selectable = c.current && c.day <= 28;
          const selected = selectable && value === c.day;
          return (
            <button
              key={i}
              type="button"
              className={`month-cal__day${selected ? " month-cal__day--selected" : ""}${!c.current ? " month-cal__day--other" : ""}${!selectable && c.current ? " month-cal__day--disabled" : ""}`}
              disabled={!selectable}
              aria-pressed={selected || undefined}
              onClick={() => selectable && onChange(c.day)}
            >
              {c.day}
            </button>
          );
        })}
      </div>
      <p className="mini-cal__hint">
        {value !== null
          ? `Executa todo dia ${value}`
          : "Selecione um dia do mês (1–28)"}
      </p>
    </div>
  );
}

export function MiniCalendar({
  periodicidade,
  value,
  onChange,
}: {
  periodicidade: PeriodicidadeBalanco;
  value: number | null;
  onChange: (v: number | null) => void;
}) {
  if (periodicidade === "SEMANAL") {
    return (
      <div className="mini-cal">
        <div className="mini-cal__week" role="group" aria-label="Dia da semana">
          {WEEK_DAYS.map((d) => (
            <button
              key={d.value}
              type="button"
              aria-pressed={value === d.value}
              onClick={() => onChange(d.value)}
            >
              {d.label}
            </button>
          ))}
        </div>
        {value !== null && (
          <span className="mini-cal__hint">
            Toda {WEEK_DAYS.find((d) => d.value === value)?.label.toLowerCase()}
          </span>
        )}
      </div>
    );
  }

  return <MonthCalendar value={value} onChange={(d) => onChange(d)} />;
}

const WEEKDAY_LABELS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];

const WEEK_DAYS: { label: string; value: number }[] = [
  { label: "Seg", value: 1 },
  { label: "Ter", value: 2 },
  { label: "Qua", value: 3 },
  { label: "Qui", value: 4 },
  { label: "Sex", value: 5 },
  { label: "Sáb", value: 6 },
  { label: "Dom", value: 7 },
];

const MONTH_NAMES = [
  "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
  "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
];
