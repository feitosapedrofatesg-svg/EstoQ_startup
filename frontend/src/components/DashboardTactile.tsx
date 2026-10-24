import {
  createContext, useCallback, useContext, useEffect, useRef, useState,
  type ButtonHTMLAttributes, type HTMLAttributes, type ReactNode,
} from "react";
import { useAnimate, useReducedMotion, type Transition } from "motion/react";

const TactileContext = createContext<{ reducedMotion: boolean } | null>(null);
const MOTION_ENABLED = { reducedMotion: false };
const MOTION_REDUCED = { reducedMotion: true };
// Originkit: pressão rápida e retorno por mola. Profundidade compacta para a grade existente.
const DX = -1;
const DY = 6;
const PRESS: Transition = { type: "tween", ease: "easeOut", duration: 0.05 };
const RELEASE: Transition = { type: "spring", stiffness: 800, damping: 60, mass: 1 };

type Events<T extends HTMLElement> = Pick<HTMLAttributes<T>,
  "onPointerDown" | "onPointerUp" | "onPointerCancel" | "onPointerLeave" | "onKeyDown" | "onKeyUp" | "onBlur">;

export function DashboardTactile({ children }: { children: ReactNode }) {
  const initialReducedMotion = useReducedMotion();
  const [reducedMotion, setReducedMotion] = useState(initialReducedMotion === true);
  useEffect(() => {
    // A preferência também pode mudar enquanto a página está aberta.
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReducedMotion(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);
  return <TactileContext.Provider value={reducedMotion ? MOTION_REDUCED : MOTION_ENABLED}>
    <div className="dashboard-tactile" data-reduced-motion={reducedMotion}>{children}</div>
  </TactileContext.Provider>;
}

export function useDashboardTactile<T extends HTMLElement>(disabled = false, original: Events<T> = {}) {
  const context = useContext(TactileContext);
  const enabled = context !== null;
  const reducedMotion = context?.reducedMotion ?? false;
  const [scope, animate] = useAnimate<T>();
  const animation = useRef<{ stop: () => void } | null>(null);
  const pressed = useRef(false);
  const pointer = useRef<number | null>(null);

  const press = useCallback((down: boolean) => {
    const el = scope.current;
    if (!el || !enabled || disabled || reducedMotion) return;
    pressed.current = down;
    animation.current?.stop();
    const base = getComputedStyle(el).getPropertyValue("--dashboard-tactile-base").trim();
    animation.current = animate(el, {
      x: down ? DX : 0,
      y: down ? DY : 0,
      boxShadow: `${down ? 0 : DX}px ${down ? 0 : DY}px 0px 0px ${base}`,
    }, down ? PRESS : RELEASE);
  }, [animate, disabled, enabled, reducedMotion, scope]);

  const release = useCallback(() => {
    pointer.current = null;
    if (pressed.current) press(false);
  }, [press]);

  useEffect(() => {
    if (!enabled) return;
    const releasePointer = (e: PointerEvent) => {
      if (pointer.current === e.pointerId) release();
    };
    window.addEventListener("pointerup", releasePointer);
    window.addEventListener("pointercancel", releasePointer);
    window.addEventListener("blur", release);
    return () => {
      window.removeEventListener("pointerup", releasePointer);
      window.removeEventListener("pointercancel", releasePointer);
      window.removeEventListener("blur", release);
      animation.current?.stop();
    };
  }, [enabled, release]);

  useEffect(() => {
    if (enabled && !disabled && !reducedMotion) return;
    animation.current?.stop();
    pressed.current = false;
    pointer.current = null;
    const el = scope.current;
    if (el) {
      const base = getComputedStyle(el).getPropertyValue("--dashboard-tactile-base").trim();
      // Zera também os valores internos do Motion para permitir reativar o efeito.
      animation.current = animate(el, { x: 0, y: 0,
        boxShadow: enabled && !disabled ? `${DX}px ${DY}px 0px 0px ${base}` : "none",
      }, { duration: 0 });
    }
  }, [animate, disabled, enabled, reducedMotion, scope]);

  const events: Events<T> = enabled && !disabled ? {
    onPointerDown: e => {
      original.onPointerDown?.(e);
      if (e.defaultPrevented || e.button !== 0 || !e.isPrimary || reducedMotion) return;
      pointer.current = e.pointerId;
      press(true);
    },
    onPointerUp: e => { original.onPointerUp?.(e); if (pointer.current === e.pointerId) release(); },
    onPointerCancel: e => { original.onPointerCancel?.(e); release(); },
    onPointerLeave: e => { original.onPointerLeave?.(e); release(); },
    onKeyDown: e => {
      original.onKeyDown?.(e);
      if (!e.defaultPrevented && !e.repeat && (e.key === "Enter" || (e.key === " " && e.currentTarget.tagName === "BUTTON"))) press(true);
    },
    onKeyUp: e => { original.onKeyUp?.(e); if (e.key === "Enter" || e.key === " ") release(); },
    onBlur: e => { original.onBlur?.(e); release(); },
  } : {};

  return { ref: enabled ? scope : undefined, className: enabled && !disabled ? "dashboard-tactile-control" : "", events };
}

/** Preserva os botões de texto dos alertas sem trocar sua apresentação por um botão preenchido. */
export function DashboardTactileAction({ className = "", disabled, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  const tactile = useDashboardTactile<HTMLButtonElement>(disabled, props);
  return <button type="button" {...props} disabled={disabled} ref={tactile.ref}
    className={`${className} ${tactile.className}`} {...tactile.events} />;
}
