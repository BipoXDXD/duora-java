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

