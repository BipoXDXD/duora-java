import { useLayoutEffect } from "react";

/**
 * Ao entrar na tela cheia, o foco que ficou num controle do painel (agora escondido) vai para o
 * título do resultado. Foco no resultado, na cena ou no topo (que continua à vista, com o Sair) fica
 * onde está.
 */
export function useFocoDaTelaCheia(telaCheia: boolean): void {
  useLayoutEffect(() => {
    if (telaCheia && !document.activeElement?.closest("#resultado, .cena, .topo")) {
      document.querySelector<HTMLElement>("#resultado-titulo")?.focus();
    }
  }, [telaCheia]);
}
