import { type ButtonHTMLAttributes, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, type IconName } from "../Icon";


import { useDashboardTactile } from "../DashboardTactile";




type BtnVariant = "accent" | "ink" | "danger" | "good" | "outline" | "ghost" | "link";

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
  const tactile = useDashboardTactile<HTMLButtonElement>(disabled || loading, rest);
  return (
    <button
      type="button"
      ref={tactile.ref}
      className={`btn btn--${variant} btn--${size} ${block ? "btn--block" : ""} ${className} ${tactile.className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
      {...tactile.events}
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
  const tactile = useDashboardTactile<HTMLAnchorElement>();
  return (
    <Link
      to={to}
      ref={tactile.ref}
      className={`btn btn--${variant} btn--${size} ${block ? "btn--block" : ""} ${className} ${tactile.className}`}
      {...tactile.events}
    >
      {icon && <Icon name={icon} size={size === "lg" ? 20 : 17} />}
      {children}
    </Link>
  );
}
