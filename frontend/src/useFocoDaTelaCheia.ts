import { useLayoutEffect } from "react";

/**
 * Ao entrar na tela cheia, o foco que ficou num controle do painel ou do topo (agora escondidos) vai
 * para o título do resultado. Foco já no resultado ou na cena fica onde está.
 */
export function useFocoDaTelaCheia(telaCheia: boolean): void {
  useLayoutEffect(() => {
    if (telaCheia && !document.activeElement?.closest("#resultado, .cena")) {
      document.querySelector<HTMLElement>("#resultado-titulo")?.focus();
    }
  }, [telaCheia]);
}
