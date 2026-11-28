// Registro do service worker: o app abre sem rede e pode ser instalado no celular.

import { CAMINHO_DA_VERIFICACAO } from "./rotas/caminhosDaCasca";

/**
 * Registra depois do load: o install baixa o shell inteiro, e antes disso disputaria a rede com as
 * imagens da primeira tela. O /sw.js é um script clássico (o build o gera como IIFE). O documento
 * da verificação não registra: ele vive isolado, com a política de segurança dele.
 */
export function registrarServiceWorker(caminho: string = location.pathname): void {
  // No vite dev não há /sw.js (o build o gera), e o fallback devolveria o HTML com um aviso.
  if (!import.meta.env.PROD || !("serviceWorker" in navigator) || caminho === CAMINHO_DA_VERIFICACAO) {
    return;
  }
  if (document.readyState === "complete") {
    void registrar();
  } else {
    window.addEventListener("load", () => void registrar(), { once: true });
  }
}

async function registrar(): Promise<void> {
  try {
    // Com service workers bloqueados (testes), o register devolve undefined: nada a fazer.
    await navigator.serviceWorker.register("/sw.js", { scope: "/", type: "classic" });
  } catch (erro) {
    // Sem o worker o app funciona igual, só não abre offline; não vale um erro na tela.
    console.warn("Service worker não registrado:", erro);
  }
}
