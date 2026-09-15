import {
  useEffect,
  useId,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from "react";
import { Link } from "react-router-dom";
import { Icon, type IconName } from "./Icon";
import { parseDecimal } from "../lib/format";

/* ------------------------------------------------------------------ Button */

type BtnVariant = "accent" | "ink" | "danger" | "outline" | "ghost" | "link";

interface BtnProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: BtnVariant;
  size?: "md" | "sm" | "lg";
  icon?: IconName;
  loading?: boolean;
  block?: boolean;
}

export function Button({
  variant = "accent",
  size = "md",
  icon,
  loading,
  block,
  className = "",
  children,
  disabled,
  ...rest
}: BtnProps) {
  return (
    <button
      type="button"
      className={`btn btn--${variant} btn--${size} ${block ? "btn--block" : ""} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? (
        <span className="spinner spinner--sm" aria-hidden="true" />
      ) : (
        icon && <Icon name={icon} size={size === "lg" ? 20 : 17} />
      )}
      {children}
    </button>
  );
}

export function LinkButton({
  to,
  variant = "accent",
  size = "md",
  icon,
  block,
  className = "",
  children,
}: {
  to: string;
  variant?: BtnVariant;
  size?: "md" | "sm" | "lg";
  icon?: IconName;
  block?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <Link
      to={to}
      className={`btn btn--${variant} btn--${size} ${block ? "btn--block" : ""} ${className}`}
    >
      {icon && <Icon name={icon} size={size === "lg" ? 20 : 17} />}
      {children}
    </Link>
  );
}

/* ------------------------------------------------------------------- Card */

export function Card({
  title,
  actions,
  children,
  className = "",
  bodyClassName = "",
}: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="card__head">
          {title && <h2 className="card__title">{title}</h2>}
          {actions && <div className="card__actions">{actions}</div>}
        </header>
      )}
      <div className={`card__body ${bodyClassName}`}>{children}</div>
    </section>
  );
}

export function StatCard({
  label,
  value,
  sub,
  tone = "neutral",
  icon,
  big,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  tone?: "neutral" | "accent" | "good" | "warn" | "bad" | "info";
  icon?: IconName;
  big?: boolean;
}) {
  return (
    <div className={`stat stat--${tone}`}>
      <div className="stat__row">
        <span className="stat__label">{label}</span>
        {icon && (
          <span className="stat__icon" aria-hidden="true">
            <Icon name={icon} size={18} />
          </span>
        )}
      </div>
      <div className={`stat__value ${big ? "stat__value--big" : ""}`}>{value}</div>
      {sub && <div className="stat__sub">{sub}</div>}
    </div>
  );
}

/* ------------------------------------------------------------------ Forms */

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
  const id = htmlFor ?? useId();
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
    <Field label={label} htmlFor={htmlFor} hint={hint} error={error} required={required}>
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

/* -------------------------------------------------------------- SearchSelect */

export interface SearchOption {
  value: string;
  label: string;
  sub?: string;
}

export function SearchSelect({
  label,
  htmlFor,
  options,
  value,
  onValue,
  placeholder,
  required,
  hint,
  error,
}: {
  label: string;
  htmlFor?: string;
  options: SearchOption[];
  value: string;
  onValue: (v: string) => void;
  placeholder?: string;
  required?: boolean;
  hint?: ReactNode;
  error?: string | null;
}) {
  const id = htmlFor ?? useId();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const boxRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listId = `${id}-list`;

  const selected = options.find((o) => o.value === value);

  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  const filtered = options.filter((o) =>
    `${o.label} ${o.sub ?? ""}`.toLowerCase().includes(query.trim().toLowerCase())
  );

  const pick = (v: string) => {
    onValue(v);
    setOpen(false);
    setQuery("");
  };

  const onInputKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setOpen(true);
      setActive((a) => Math.min(a + 1, filtered.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (open && filtered[active]) pick(filtered[active].value);
      else setOpen(true);
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  };

  return (
    <Field
      label={label}
      htmlFor={id}
      hint={hint}
      error={error}
      required={required}
    >
      <div className="searchselect" ref={boxRef}>
        <button
          type="button"
          className={`searchselect__trigger ${error ? "input--invalid" : ""}`}
          onClick={() => {
            setOpen((o) => !o);
            setQuery("");
            setActive(0);
            window.setTimeout(() => inputRef.current?.focus(), 0);
          }}
          aria-haspopup="listbox"
          aria-expanded={open}
        >
          <span className={`searchselect__value ${!value ? "searchselect__value--placeholder" : ""}`}>
            {value ? selected?.label ?? value : (placeholder ?? "Escolha um item")}
            {value && selected?.sub ? <span className="searchselect__sub">{selected.sub}</span> : null}
          </span>
          <Icon name="chevron-down" size={16} className="searchselect__chevron" />
        </button>
        {open && (
          <div className="searchselect__menu" role="listbox" id={listId}>
            <div className="searchselect__search">
              <Icon name="search" size={15} />
              <input
                ref={inputRef}
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setActive(0);
                }}
                onKeyDown={onInputKey}
                placeholder="Digite para buscar…"
                aria-label={`Buscar ${label}`}
              />
            </div>
            <ul className="searchselect__list">
              {filtered.length === 0 && (
                <li className="searchselect__empty">Nada encontrado.</li>
              )}
              {filtered.map((o, i) => (
                <li
                  key={o.value}
                  role="option"
                  aria-selected={o.value === value}
                  className={`searchselect__opt ${i === active ? "searchselect__opt--active" : ""}`}
                  onMouseEnter={() => setActive(i)}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    pick(o.value);
                  }}
                >
                  <span>{o.label}</span>
                  {o.sub && <span className="searchselect__sub">{o.sub}</span>}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </Field>
  );
}

/* -------------------------------------------------------------------- Badge */

type BadgeTone = "neutral" | "accent" | "good" | "warn" | "bad" | "info";

export function Badge({ tone = "neutral", children }: { tone?: BadgeTone; children: ReactNode }) {
  return <span className={`badge badge--${tone}`}>{children}</span>;
}

export function StatusBadge({
  label,
  tone,
  children,
}: {
  label?: string;
  tone: BadgeTone;
  children?: ReactNode;
}) {
  return (
    <span className={`badge badge--${tone}`}>
      <span className="badge__dot" aria-hidden="true" />
      {children ?? label}
    </span>
  );
}

/* -------------------------------------------------------------------- Modal */

export function Modal({
  open,
  onClose,
  title,
  children,
  width = "md",
  footer,
  labelledBy,
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  width?: "sm" | "md" | "lg";
  footer?: ReactNode;
  labelledBy?: string;
}) {
  const id = useId();
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const prev = document.activeElement as HTMLElement | null;
    ref.current?.querySelector<HTMLElement>("[data-autofocus], button, input, select, textarea")?.focus();
    return () => {
      document.removeEventListener("keydown", onKey);
      prev?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        className={`modal modal--${width}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy ?? id}
        ref={ref}
      >
        <div className="modal__head">
          <h2 className="modal__title" id={labelledBy ?? id}>
            {title}
          </h2>
          <button className="modal__x" onClick={onClose} aria-label="Fechar janela">
            <Icon name="x" size={18} />
          </button>
        </div>
        <div className="modal__body">{children}</div>
        {footer && <div className="modal__foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Confirm({
  open,
  title,
  message,
  confirmLabel = "Confirmar",
  cancelLabel = "Cancelar",
  danger,
  loading,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      width="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button variant={danger ? "danger" : "accent"} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className={`confirm-message ${danger ? "confirm-message--danger" : ""}`}>
        {danger ? (
          <span className="confirm-message__icon" aria-hidden="true">
            <Icon name="alert-triangle" size={22} />
          </span>
        ) : null}
        <div className="confirm-message__text">{message}</div>
      </div>
    </Modal>
  );
}

/* ------------------------------------------------------------------ Table */

export function DataTable({
  headers,
  children,
  caption,
}: {
  headers: ReactNode[];
  children: ReactNode;
  caption?: string;
}) {
  return (
    <div className="table-wrap">
      <table className="table">
        {caption && <caption className="visually-hidden">{caption}</caption>}
        <thead>
          <tr>
            {headers.map((h, i) => (
              <th key={i} scope="col">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

/* -------------------------------------------------------------- Empty state */

export function EmptyState({
  title,
  text,
  action,
  icon = "box",
}: {
  title: string;
  text?: string;
  action?: ReactNode;
  icon?: IconName;
}) {
  return (
    <div className="empty">
      <span className="empty__icon" aria-hidden="true">
        <Icon name={icon} size={30} />
      </span>
      <p className="empty__title">{title}</p>
      {text && <p className="empty__text">{text}</p>}
      {action && <div className="empty__action">{action}</div>}
    </div>
  );
}

/* ------------------------------------------------------------------ Tabs */

export function Tabs({
  items,
  active,
  onChange,
}: {
  items: { id: string; label: string }[];
  active: string;
  onChange: (id: string) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {items.map((t) => (
        <button
          key={t.id}
          type="button"
          role="tab"
          aria-selected={active === t.id}
          className={`tab ${active === t.id ? "tab--active" : ""}`}
          onClick={() => onChange(t.id)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------- Page header */

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