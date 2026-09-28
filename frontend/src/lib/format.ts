const BRL = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const NUM = new Intl.NumberFormat("pt-BR", {
  maximumFractionDigits: 3,
});

const PCT = new Intl.NumberFormat("pt-BR", {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

export function fmtMoney(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(v)) return "—";
  return BRL.format(v);
}

export function fmtNum(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(v)) return "—";
  return NUM.format(v);
}

export function fmtPct(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(v)) return "—";
  return `${PCT.format(v)}%`;
}

export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso.length <= 10 ? `${iso}T00:00:00` : iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("pt-BR");
}

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function fmtBytes(bytes: number): string {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export function fmtDiaSemana(dia: string): string {
  const map: Record<string, string> = {
    MONDAY: "Segunda",
    TUESDAY: "Terça",
    WEDNESDAY: "Quarta",
    THURSDAY: "Quinta",
    FRIDAY: "Sexta",
    SATURDAY: "Sábado",
    SUNDAY: "Domingo",
  };
  return map[dia] ?? dia;
}

export function fmtDataHoje(): string {
  return new Date().toLocaleDateString("pt-BR", {
    weekday: "long",
    day: "2-digit",
    month: "long",
  });
}

export function hojeISO(): string {
  return new Date().toISOString().slice(0, 10);
}

export function diasAtrasISO(dias: number): string {
  const d = new Date();
  d.setDate(d.getDate() - dias);
  return d.toISOString().slice(0, 10);
}

// Aceita as duas grafias que aparecem em digitação livre no pt-BR.
// A ambiguidade do "." é resolvida pelo número de dígitos à direita dele:
//   "1.500"    -> 1500   (3 dígitos à direita => separador de milhar)
//   "1.5"      -> 1.5    (1 dígito à direita  => decimal)
//   "10.50"    -> 10.5   (2 dígitos à direita => decimal)
//   "1.234,56" -> 1234.56 (vírgula presente   => decimal, ponto é milhar)
export function parseDecimal(s: string): number | null {
  const t = s.trim();
  if (!t) return null;

  const hasComma = t.includes(",");
  const hasDot = t.includes(".");
  let normalized: string;

  if (hasComma && hasDot) {
    // Vírgula manda: ponto é separador de milhar.
    normalized = t.replace(/\./g, "").replace(",", ".");
  } else if (hasComma) {
    normalized = t.replace(",", ".");
  } else if (hasDot) {
    const decimals = t.length - t.lastIndexOf(".") - 1;
    const isThousands =
      decimals === 3 || (t.match(/\./g) ?? []).length > 1;
    normalized = isThousands ? t.replace(/\./g, "") : t;
  } else {
    normalized = t;
  }

  const n = Number(normalized);
  return Number.isFinite(n) ? n : null;
}

export function hojeUTC(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate()
  ).padStart(2, "0")}`;
}