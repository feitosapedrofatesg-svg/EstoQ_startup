import { useRef, useState } from "react";
import { flushSync } from "react-dom";
import { Field, Input } from "../UI";

/** O primeiro toque não abre o teclado nativo; a digitação é uma ação explícita. */
export function TextoPorToque({ id, label, value, onValue, placeholder, hint, maxLength }: {
  id: string; label: string; value: string; onValue: (valor: string) => void;
  placeholder?: string; hint?: string; maxLength?: number;
}) {
  const [digitando, setDigitando] = useState(false);
  const ultimoToque = useRef<number | null>(null);
  return <Field label={label} htmlFor={id} hint={hint ?? "Toque duas vezes para digitar."}>
    <Input id={id} value={value} placeholder={placeholder} maxLength={maxLength}
      readOnly={!digitando} inputMode={digitando ? "text" : "none"} onChange={e => onValue(e.target.value)}
      onBlur={() => { setDigitando(false); ultimoToque.current = null; }}
      onPointerDown={e => {
        const agora = performance.now();
        if (ultimoToque.current !== null && agora - ultimoToque.current <= 400) {
          flushSync(() => setDigitando(true)); e.currentTarget.focus(); ultimoToque.current = null;
        } else { ultimoToque.current = agora; if (!digitando) e.preventDefault(); }
      }}
      onKeyDown={e => { if (e.key === "Enter" && !digitando) { e.preventDefault(); setDigitando(true); } }} />
  </Field>;
}
