import { type ReactNode } from "react";

import { Icon, type IconName } from "../Icon";







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
