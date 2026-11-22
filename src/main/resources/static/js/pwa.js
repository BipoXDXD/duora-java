// Registro do service worker: o app abre sem rede e pode ser instalado no celular.

export async function registrarServiceWorker() {
  if (!("serviceWorker" in navigator)) {
    return;
  }
  try {
    // Com service workers bloqueados (testes), o register devolve undefined: nada a fazer.
    await navigator.serviceWorker.register("/sw.js", { scope: "/" });
  } catch (erro) {
    // Sem o worker o app funciona igual, só não abre offline; não vale um erro na tela.
    console.warn("Service worker não registrado:", erro);
  }
}
