import { useId, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, type IconName } from "../Icon";


import { useDashboardTactile } from "../DashboardTactile";

import { Button } from "./Botoes";



export function ListToggle({
  expanded,
  onToggle,
  controls,
  label,
  subject = "lista",
}: {
  expanded: boolean;
  onToggle: () => void;
  controls: string;
  label: string;
  subject?: string;
}) {
  return (
    <Button
      variant={expanded ? "outline" : "accent"}
      className="list-toggle"
      aria-expanded={expanded}
      aria-controls={controls}
      aria-label={`${expanded ? "Minimizar" : "Maximizar"} ${label}`}
      onClick={onToggle}
    >
      <span className={`list-toggle__icon${expanded ? " list-toggle__icon--open" : ""}`} aria-hidden="true">
        <Icon name="chevron-down" size={20} />
      </span>
      {expanded ? "Minimizar" : "Maximizar"} {subject}
    </Button>
  );
}

export function Card({
  title,
  actions,
  children,
  className = "",
  bodyClassName = "",
  collapsible = false,
  collapseLabel,
}: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
  collapsible?: boolean;
  collapseLabel?: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const contentId = useId();
  const label = collapseLabel ?? (typeof title === "string" ? title : "lista");
  return (
    <section className={`card ${collapsible ? "card--collapsible" : ""} ${collapsible && !expanded ? "card--collapsed" : ""} ${className}`}>
      {(title || actions || collapsible) && (
        <header className="card__head">
          {title && <h2 className="card__title">{title}</h2>}
          {(actions || collapsible) && <div className="card__actions">
            {(!collapsible || expanded) && actions}
            {collapsible && (
              <ListToggle expanded={expanded} onToggle={() => setExpanded((value) => !value)}
                controls={contentId} label={label} />
            )}
          </div>}
        </header>
      )}
      <div id={contentId} hidden={collapsible && !expanded} className={`card__body ${bodyClassName}`}>{children}</div>
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
  to,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  tone?: "neutral" | "accent" | "good" | "warn" | "bad" | "info";
  icon?: IconName;
  big?: boolean;
  to?: string;
}) {
  const tactile = useDashboardTactile<HTMLAnchorElement>(!to);
  const content = (
    <>
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
    </>
  );
  return to ? (
    <Link to={to} ref={tactile.ref} className={`stat stat--${tone} stat--link ${tactile.className}`} {...tactile.events}>
      {content}
    </Link>
  ) : (
    <div className={`stat stat--${tone}`}>{content}</div>
  );
}
