import { type ReactNode } from "react";









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
