import type { ReactElement, SVGProps } from "react";

const PATHS: Record<string, ReactElement> = {
  dashboard: (
    <>
      <path d="M4 4h16v16H4z" />
      <path d="M9 8v8M15 8v8" />
    </>
  ),
  boxes: (
    <>
      <path d="M3.5 7.5 12 3l8.5 4.5v9L12 21l-8.5-4.5v-9Z" />
      <path d="M3.5 7.5 12 12l8.5-4.5M12 12v9" />
    </>
  ),
  arrows: (
    <>
      <path d="M3 9h13l-4-4" />
      <path d="M21 15H8l4 4" />
    </>
  ),
  "box-open": (
    <>
      <path d="M4 8V5a1 1 0 0 1 1-1h4.5L12 6h7a1 1 0 0 1 1 1v3" />
      <path d="M4 8h16V20H4V8Z" />
      <path d="M9 11h6" />
    </>
  ),
  scale: (
    <>
      <path d="M12 3v18" />
      <path d="M8 21h8" />
      <path d="M7 3a5 12 0 0 0 10 0" />
      <path d="M12 3l2 4" transform="translate(4.5 0)" />
      <path d="M6.5 8.5 3 14a4 4 0 0 0 7 0l-3.5-5.5ZM17.5 8.5 14 14a4 4 0 0 0 7 0l-3.5-5.5Z" />
    </>
  ),
  chart: (
    <>
      <path d="M4 20h16" />
      <path d="M7 20v-7M12 20V6M17 20v-10" />
    </>
  ),
  archive: (
    <>
      <path d="M4 7h16v13H4z" />
      <path d="M3 4h18v3H3z" />
      <path d="M9 11h6" />
    </>
  ),
  users: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
      <path d="M16 5.2a3.5 3.5 0 0 1 0 5.6M17.5 14a6.5 6.5 0 0 1 4 6" />
    </>
  ),
  bell: (
    <>
      <path d="M6 9a6 6 0 0 1 12 0c0 5 2 6.5 2 6.5H4S6 14 6 9Z" />
      <path d="M10 19a2 2 0 0 0 4 0" />
    </>
  ),
  "check-circle": (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m8.5 12 2.5 2.5 5-5.5" />
    </>
  ),
  "alert-circle": (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.5V13" />
      <path d="M12 16.4h.01" />
    </>
  ),
  "alert-triangle": (
    <>
      <path d="M12 3.5 22 20H2L12 3.5Z" />
      <path d="M12 10v4" />
      <path d="M12 16.6h.01" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5" />
      <path d="M12 8h.01" />
    </>
  ),
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  x: (
    <>
      <path d="M6 6l12 12" />
      <path d="M18 6 6 18" />
    </>
  ),
  plus: (
    <>
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m20 20-3.8-3.8" />
    </>
  ),
  download: (
    <>
      <path d="M12 3v12" />
      <path d="m7 10 5 5 5-5" />
      <path d="M4 20h16" />
    </>
  ),
  trash: (
    <>
      <path d="M5 7h14" />
      <path d="M9 7V4h6v3" />
      <path d="M7 7l1 13h8l1-13" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3.5 2" />
    </>
  ),
  logout: (
    <>
      <path d="M9 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4" />
      <path d="m16 8 4 4-4 4" />
      <path d="M20 12H9.5" />
    </>
  ),
  menu: (
    <>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </>
  ),
  "chevron-down": <path d="m6 9 6 6 6-6" />,
  edit: (
    <>
      <path d="M4 20h4l12-12-4-4L4 16v4Z" />
      <path d="m13.5 6.5 4 4" />
    </>
  ),
  box: (
    <>
      <path d="M3.5 8 12 3.5 20.5 8v8L12 20.5 3.5 16V8Z" />
      <path d="M3.5 8l8.5 5 8.5-5M12 13v7.5" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 12a8 8 0 1 1-2.34-5.66L20 8" />
      <path d="M20 3v5h-5" />
    </>
  ),
  "clipboard-list": (
    <>
      <rect x="5" y="4" width="14" height="17" rx="2" />
      <path d="M9 4v2h6V4" />
      <path d="M9 11h6M9 15h6" />
    </>
  ),
  alert: (
    <>
      <path d="M12 3.5 2.5 20h19L12 3.5Z" />
      <path d="M12 9.5V14" />
      <path d="M12 16.8h.01" />
    </>
  ),
  tags: (
    <>
      <path d="M3.5 3.5h7L20 13l-7 7-9.5-9.5v-7Z" />
      <circle cx="8" cy="8" r="1.4" />
    </>
  ),
};

export type IconName = keyof typeof PATHS;

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName;
  size?: number;
}

export function Icon({ name, size = 20, ...rest }: IconProps) {
  const node = PATHS[name];
  if (!node) return null;
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {node}
    </svg>
  );
}