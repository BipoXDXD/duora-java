// O bloco do par e do aviso fica preso abaixo da cena no celular alto (destaque.css). O
// scroll-padding-top precisa da altura real dele: um aviso longo ou a fonte grande do sistema quebram
// em mais linhas do que a estimativa do CSS, e o item focado ficaria parcialmente coberto.

import { useLayoutEffect } from "react";

const VARIAVEL = "--altura-par-e-aviso";

/** Mede o .par-e-aviso e grava a altura em --altura-par-e-aviso (CSSOM, pela CSP); sem ResizeObserver, vale a estimativa do CSS. */
export function useAlturaDoParEAviso(): void {
  useLayoutEffect(() => {
    const bloco = document.querySelector<HTMLElement>(".par-e-aviso");
    // O jsdom e navegadores antigos não têm ResizeObserver.
    if (bloco === null || typeof globalThis.ResizeObserver === "undefined") {
      return;
    }
    const raiz = document.documentElement.style;
    const medir = () => {
      // A folga é a do anel de foco, a mesma da estimativa do CSS.
      raiz.setProperty(VARIAVEL, `calc(${String(bloco.getBoundingClientRect().height)}px + var(--e-2))`);
    };
    medir();
    const observador = new ResizeObserver(medir);
    observador.observe(bloco);
    return () => {
      observador.disconnect();
      raiz.removeProperty(VARIAVEL);
    };
  }, []);
}
