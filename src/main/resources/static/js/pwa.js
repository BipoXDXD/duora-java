// Registro do service worker: o app abre sem rede e pode ser instalado no celular.

/**
 * Registra depois do load: o install baixa o shell inteiro, e antes disso disputaria a rede com as
 * imagens da primeira tela.
 */
export function registrarServiceWorker() {
  if (!("serviceWorker" in navigator)) {
    return;
  }
  if (document.readyState === "complete") {
    registrar();
  } else {
    window.addEventListener("load", registrar, { once: true });
  }
}

async function registrar() {
  try {
    // Com service workers bloqueados (testes), o register devolve undefined: nada a fazer.
    await navigator.serviceWorker.register("/sw.js", { scope: "/" });
  } catch (erro) {
    // Sem o worker o app funciona igual, só não abre offline; não vale um erro na tela.
    console.warn("Service worker não registrado:", erro);
  }
}
