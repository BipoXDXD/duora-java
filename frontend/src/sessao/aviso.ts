// O #aviso (aria-live) escrito à mão, fora do React: cada aviso troca os nós de texto, e essa
// mutação é o que o leitor de tela anuncia, mesmo quando o texto se repete. O React não geraria
// mutação para um texto igual e juntaria avisos seguidos no mesmo render.

export type TipoDeAviso = "info" | "erro";

export interface Aviso {
  avisar: (texto: string, tipo?: TipoDeAviso) => void;
  /**
   * Aviso com uma parte que muda sem ser reanunciada: o leitor de tela ouve `fixo` uma vez;
   * `variavel` fica visível, mas aria-hidden, e só ela é atualizada enquanto `fixo` não mudar.
   */
  avisarComDetalhe: (fixo: string, variavel: string, tipo?: TipoDeAviso) => void;
}

export function criarAviso(el: HTMLElement): Aviso {
  return {
    avisar(texto, tipo = "info") {
      el.dataset.tipo = tipo;
      el.textContent = texto;
    },
    avisarComDetalhe(fixo, variavel, tipo = "info") {
      el.dataset.tipo = tipo;
      const [texto, detalhe] = el.childNodes;
      if (texto instanceof Text && texto.data === fixo && detalhe instanceof HTMLElement && detalhe.dataset.detalhe) {
        detalhe.textContent = ` ${variavel}`;
        return;
      }
      const span = document.createElement("span");
      span.dataset.detalhe = "true";
      span.setAttribute("aria-hidden", "true");
      span.textContent = ` ${variavel}`;
      el.replaceChildren(document.createTextNode(fixo), span);
    },
  };
}
