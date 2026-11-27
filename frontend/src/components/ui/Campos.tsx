import { traduzir, idiomaAtual } from "../../lib/i18n";
import { useEffect, useId, useState, useRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from "react";

import { Icon } from "../Icon";
import { parseDecimal } from "../../lib/format";






export function Field({
  label,
  htmlFor,
  hint,
  error,
  required,
  children,
  className = "",
}: {
  label: string;
  htmlFor?: string;
  hint?: ReactNode;
  error?: string | null;
  required?: boolean;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`field ${className}`}>
      <label className="field__label" htmlFor={htmlFor}>
        {typeof label === "string" ? traduzir(label) : label}
        {required && (
          <span className="field__req" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children}
      {error ? (
        <p className="field__error" role="alert">
          {traduzir(error)}
        </p>
      ) : hint ? (
        <p className="field__hint">{hint}</p>
      ) : null}
    </div>
  );
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export function Input({ className = "", invalid, ...rest }: InputProps) {
  return <input className={`input ${invalid ? "input--invalid" : ""} ${className}`} {...rest} />;
}

export function Textarea({
  className = "",
  invalid,
  ...rest
}: InputHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return <textarea className={`input input--area ${invalid ? "input--invalid" : ""} ${className}`} {...rest} />;
}

export function Select({
  className = "",
  invalid,
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return (
    <div className="select-wrap">
      <select className={`input select ${invalid ? "input--invalid" : ""} ${className}`} {...rest}>
        {children}
      </select>
      <span className="select-chevron" aria-hidden="true">
        <Icon name="chevron-down" size={16} />
      </span>
    </div>
  );
}

export function NumberField({
  label,
  htmlFor,
  value,
  onValue,
  min,
  max,
  step,
  placeholder,
  hint,
  error,
  required,
  suffix,
}: {
  label: string;
  htmlFor?: string;
  value: number | null;
  onValue: (v: number | null) => void;
  min?: number;
  max?: number;
  step?: number;
  placeholder?: string;
  hint?: ReactNode;
  error?: string | null;
  required?: boolean;
  suffix?: string;
}) {
  const autoId = useId();
  const id = htmlFor ?? autoId;
  const idioma=idiomaAtual();
  const formatar=(numero:number)=>new Intl.NumberFormat(idioma,{useGrouping:false,maximumFractionDigits:12}).format(numero);
  const [text, setText] = useState(value === null ? "" : formatar(value));
  const editando=useRef(false);const idiomaEdicao=useRef(idioma);
  useEffect(() => {
    if(!editando.current)setText(value === null ? "" : formatar(value));
  }, [value,idioma]);

  const commit = (raw: string) => {
    const trimmed = raw.trim();
    if (!trimmed) {
      onValue(null);
      return;
    }
    const n = parseDecimal(trimmed,idiomaEdicao.current);
    if (n === null) return;
    if (min !== undefined && n < min) {
      setText(formatar(min));
      onValue(min);
      return;
    }
    if (max !== undefined && n > max) {
      setText(formatar(max));
      onValue(max);
      return;
    }
    setText(formatar(n));
    onValue(n);
  };

  return (
    <Field label={typeof label === "string" ? traduzir(label) : label} htmlFor={id} hint={hint} error={error} required={required}>
      <div className="input-group">
        <input
          id={id}
          className={`input ${error ? "input--invalid" : ""}`}
          inputMode="decimal"
          autoComplete="off"
          placeholder={placeholder}
          value={text}
          min={min}
          max={max}
          step={step}
          onChange={(e) => {
            setText(e.target.value);
            const n = parseDecimal(e.target.value,idiomaEdicao.current);
            if (n !== null) onValue(n);
          }}
          onFocus={()=>{editando.current=true;idiomaEdicao.current=idioma;}}
          onBlur={(e) => {editando.current=false;commit(e.target.value);}}
        />
        {suffix && <span className="input-group__suffix">{suffix}</span>}
      </div>
    </Field>
  );
}
