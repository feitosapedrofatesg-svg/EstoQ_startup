import { useEffect, useId, useRef, useState, type ReactNode } from "react";

import { Icon } from "../Icon";




import { Field } from "./Campos";



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
    const onDocClick = (e: PointerEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onDocClick);
    return () => document.removeEventListener("pointerdown", onDocClick);
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
                  onClick={() => pick(o.value)}
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
