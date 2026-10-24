import { useEffect, useId, useState, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from "react";

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
        {label}
        {required && (
          <span className="field__req" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children}
      {error ? (
        <p className="field__error" role="alert">
          {error}
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
  const [text, setText] = useState(value === null ? "" : String(value).replace(".", ","));

  useEffect(() => {
    if (value === null) setText("");
    else setText(String(value).replace(".", ","));
  }, [value]);

  const commit = (raw: string) => {
    const trimmed = raw.trim();
    if (!trimmed) {
      onValue(null);
      return;
    }
    const n = parseDecimal(trimmed);
    if (n === null) return;
    if (min !== undefined && n < min) {
      setText(String(min).replace(".", ","));
      onValue(min);
      return;
    }
    if (max !== undefined && n > max) {
      setText(String(max).replace(".", ","));
      onValue(max);
      return;
    }
    setText(String(n).replace(".", ","));
    onValue(n);
  };

  return (
    <Field label={label} htmlFor={id} hint={hint} error={error} required={required}>
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
            const n = parseDecimal(e.target.value);
            if (n !== null) onValue(n);
          }}
          onBlur={(e) => commit(e.target.value)}
        />
        {suffix && <span className="input-group__suffix">{suffix}</span>}
      </div>
    </Field>
  );
}
