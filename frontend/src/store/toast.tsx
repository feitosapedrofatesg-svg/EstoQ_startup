import {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Icon } from "../components/Icon";

type Tone = "success" | "error" | "warning" | "info";

interface Toast {
  id: number;
  tone: Tone;
  title: string;
  message?: string;
}

interface ToastCtx {
  push: (tone: Tone, title: string, message?: string) => void;
  success: (title: string, message?: string) => void;
  error: (title: string, message?: string) => void;
  warning: (title: string, message?: string) => void;
  info: (title: string, message?: string) => void;
}

const Ctx = createContext<ToastCtx | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (tone: Tone, title: string, message?: string) => {
      const id = nextId.current++;
      setToasts((prev) => [...prev.slice(-3), { id, tone, title, message }]);
      window.setTimeout(() => dismiss(id), 6000);
    },
    [dismiss]
  );

  const value: ToastCtx = {
    push,
    success: (t, m) => push("success", t, m),
    error: (t, m) => push("error", t, m),
    warning: (t, m) => push("warning", t, m),
    info: (t, m) => push("info", t, m),
  };

  return (
    <Ctx.Provider value={value}>
      {children}
      <div
        className="toast-region"
        aria-live="polite"
        aria-relevant="additions"
        aria-label="Notificações"
      >
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast--${t.tone}`} role="status">
            <span className="toast__icon" aria-hidden="true">
              <Icon name={t.tone === "success" ? "check-circle" : t.tone === "error" ? "alert-circle" : t.tone === "warning" ? "alert-triangle" : "info"} size={20} />
            </span>
            <div className="toast__body">
              <p className="toast__title">{t.title}</p>
              {t.message && <p className="toast__msg">{t.message}</p>}
            </div>
            <button
              type="button"
              className="toast__close"
              onClick={() => dismiss(t.id)}
              aria-label="Fechar notificação"
            >
              <Icon name="x" size={16} />
            </button>
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}

export function useToast(): ToastCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useToast must be used inside ToastProvider");
  return ctx;
}