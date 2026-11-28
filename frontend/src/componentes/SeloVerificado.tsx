import type { ReactElement } from "react";

/** A marca do selo: decorativa, o texto ao lado já diz tudo. Fonte única (Minha conta e cartão). */
export function MarcaDoSelo(): ReactElement {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      className="size-5 shrink-0 text-verde-agua forced-colors:text-[CanvasText]"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="10" cy="10" r="8.5" />
      <path d="m6.5 10.25 2.25 2.25 4.75-5" />
    </svg>
  );
}

/** O selo de idade verificada: a marca e a palavra, para não depender do ícone nem da cor. */
export function SeloVerificado(): ReactElement {
  return (
    <span className="inline-flex items-center gap-(--e-1) text-[0.9375rem] font-semibold leading-[1.25] text-verde-agua forced-colors:text-[CanvasText]">
      <MarcaDoSelo />
      Verificado
    </span>
  );
}
