import { idiomaAtual, t } from "./i18n";

export function fmtMoney(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(v)) return "—";
  return new Intl.NumberFormat(idiomaAtual(),{style:"currency",currency:"BRL"}).format(v);
}

export function fmtNum(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(v)) return "—";
  return new Intl.NumberFormat(idiomaAtual(),{maximumFractionDigits:3}).format(v);
}

export function fmtPct(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(v)) return "—";
  return `${new Intl.NumberFormat(idiomaAtual(),{minimumFractionDigits:1,maximumFractionDigits:1}).format(v)}%`;
}

export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso.length <= 10 ? `${iso}T00:00:00` : iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString(idiomaAtual());
}

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  // O servidor grava em UTC, mas os campos LocalDateTime chegam sem fuso.
  // Preserve offsets explícitos e identifique os horários antigos como UTC.
  const temFuso = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(iso);
  const d = new Date(temFuso ? iso : `${iso}Z`);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString(idiomaAtual(), {
    timeZone:"America/Sao_Paulo",
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
  return t(map[dia] ?? dia);
}

export function fmtDataHoje(): string {
  return new Date().toLocaleDateString(idiomaAtual(), {
    timeZone:"America/Sao_Paulo",
    weekday: "long",
    day: "2-digit",
    month: "long",
  });
}

// O dia operacional segue o mesmo fuso do servidor, inclusive fora do Brasil.
function dataLocalISO(d:Date):string {
  const partes=new Intl.DateTimeFormat("en-CA",{timeZone:"America/Sao_Paulo",year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(d);
  const valor=(tipo:string)=>partes.find(p=>p.type===tipo)?.value;
  return `${valor("year")}-${valor("month")}-${valor("day")}`;
}
export function hojeISO():string {return dataLocalISO(new Date());}
export function diasAtrasISO(dias:number):string {const d=new Date(`${hojeISO()}T12:00:00-03:00`);d.setUTCDate(d.getUTCDate()-dias);return dataLocalISO(d);}

// Aceita as duas grafias que aparecem em digitação livre no pt-BR.
// A ambiguidade do "." é resolvida pelo número de dígitos à direita dele:
//   "1.500"    -> 1500   (3 dígitos à direita => separador de milhar)
//   "1.5"      -> 1.5    (1 dígito à direita  => decimal)
//   "10.50"    -> 10.5   (2 dígitos à direita => decimal)
//   "1.234,56" -> 1234.56 (vírgula presente   => decimal, ponto é milhar)
export function parseDecimal(s: string, idioma = "pt-BR"): number | null {
  const t = s.trim();
  if (!t) return null;

  const hasComma = t.includes(",");
  const hasDot = t.includes(".");
  let normalized: string;

  if (idioma === "en") {
    normalized = t.replace(/,/g, "");
  } else if (hasComma && hasDot) {
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

export function fmtLote(codigo: string | null | undefined, fabricante?: string | null): string {
  return `${codigo ?? "—"}${fabricante ? ` · ${t("fabricante")} ${fabricante}` : ""}`;
}
