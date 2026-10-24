import { useEffect, useId, useRef, type ReactNode } from "react";

import { Icon } from "../Icon";




import { Button } from "./Botoes";



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

  const fechar = useRef(onClose);
  fechar.current = onClose;
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (ref.current?.closest("[inert]")) return;
      if (e.key === "Escape") { e.preventDefault(); fechar.current(); return; }
      if (e.key !== "Tab") return;
      const itens = [...(ref.current?.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]'
      ) ?? [])].filter(item => item.getClientRects().length > 0 && !item.closest('[inert]'));
      const primeiro = itens[0]; const ultimo = itens[itens.length - 1];
      if (!primeiro) { e.preventDefault(); ref.current?.focus(); return; }
      if (e.shiftKey && (document.activeElement === primeiro || document.activeElement === ref.current)) {
        e.preventDefault(); ultimo.focus();
      } else if (!e.shiftKey && document.activeElement === ultimo) {
        e.preventDefault(); primeiro.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    const prev = document.activeElement as HTMLElement | null;
    const fundo = new Map<HTMLElement, boolean>();
    let caminho = ref.current?.parentElement ?? null;
    while (caminho && caminho !== document.body) {
      for (const irmao of [...(caminho.parentElement?.children ?? [])]) {
        if (irmao instanceof HTMLElement && irmao !== caminho && !["SCRIPT", "STYLE", "LINK"].includes(irmao.tagName)) {
          fundo.set(irmao, irmao.inert);
          irmao.inert = true;
        }
      }
      caminho = caminho.parentElement;
    }
    ref.current?.querySelector<HTMLElement>("[data-autofocus], button, input, select, textarea")?.focus();
    return () => {
      document.removeEventListener("keydown", onKey);
      fundo.forEach((valor, elemento) => { elemento.inert = valor; });
      prev?.focus?.();
    };
  }, [open]);

  if (!open) return null;

  return (
    <div className="modal-backdrop" onPointerDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        className={`modal modal--${width}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy ?? id}
        ref={ref}
        tabIndex={-1}
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
