import { useId, type ReactNode } from "react";

import { Icon } from "../Icon";







export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="pagehead">
      <div>
        <h1 className="pagehead__title">{title}</h1>
        {subtitle && <p className="pagehead__subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="pagehead__actions">{actions}</div>}
    </header>
  );
}

export function AlertBanner({
  tone = "info",
  children,
}: {
  tone?: "info" | "warn" | "bad" | "good";
  children: ReactNode;
}) {
  return (
    <div className={`alertbanner alertbanner--${tone}`} role="note">
      <Icon name={tone === "bad" ? "alert-circle" : tone === "warn" ? "alert-triangle" : tone === "good" ? "check-circle" : "info"} size={18} />
      <div>{children}</div>
    </div>
  );
}

export function Checkbox({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
}) {
  const id = useId();
  return (
    <div className="checkbox">
      <input
        type="checkbox"
        id={id}
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <label htmlFor={id}>{label}</label>
    </div>
  );
}

export function Segmented({
  items,
  value,
  onChange,
  label,
}: {
  items: { value: string; label: string }[];
  value: string;
  onChange: (v: string) => void;
  label: string;
}) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {items.map((it) => (
        <button
          key={it.value}
          type="button"
          className={`segmented__btn ${value === it.value ? "segmented__btn--active" : ""}`}
          aria-pressed={value === it.value}
          onClick={() => onChange(it.value)}
        >
          {it.label}
        </button>
      ))}
    </div>
  );
}
