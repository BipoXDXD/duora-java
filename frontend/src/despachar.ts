import type { Root } from "react-dom/client";
import { lerConvite } from "./convite";

const AVISO_DE_FALHA = "Não foi possível abrir esta página. Confira a conexão e recarregue.";

/** O terraço vive em "/" (e no index.html servido direto); todo o resto é da casca de conta. */
export function ehCaminhoDoTerraco(caminho: string): boolean {
  return caminho === "/" || caminho === "/index.html";
}

export interface DependenciasDoDespacho {
  iniciarTerraco: (raiz: HTMLElement, convite: string | null) => unknown;
  /** O main.tsx passa um import() dinâmico: o terraço não baixa React Router nem TanStack Query. */
  carregarCasca: () => Promise<{ montarCasca: (raiz: HTMLElement, local: Location, historia: History) => Root }>;
}

/**
 * Decide, pela URL da primeira carga, quem monta na raiz. No terraço, lê o convite (que limpa o
 * fragmento) e inicia o app do encontro. Fora dele, tira o esqueleto do terraço e baixa a casca sem
 * tocar no fragmento: o #t= do link mágico é lido pela própria casca.
 */
export function despachar(local: Location, historia: History, raiz: HTMLElement, deps: DependenciasDoDespacho): void {
  if (ehCaminhoDoTerraco(local.pathname)) {
    deps.iniciarTerraco(raiz, lerConvite(local, historia));
    return;
  }
  raiz.replaceChildren();
  deps.carregarCasca().then(
    ({ montarCasca }) => {
      montarCasca(raiz, local, historia);
    },
    (erro: unknown) => {
      // Sem a casca não há React montado: o aviso vai direto no DOM, e o detalhe, ao console.
      console.error("Não foi possível carregar a casca de conta:", erro);
      const aviso = document.createElement("p");
      aviso.setAttribute("role", "alert");
      aviso.className = "m-0 p-(--e-6) max-w-[40ch] text-[1.125rem] leading-[1.4]";
      aviso.textContent = AVISO_DE_FALHA;
      raiz.replaceChildren(aviso);
    },
  );
}
