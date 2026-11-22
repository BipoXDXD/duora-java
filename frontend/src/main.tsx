import "./estilos/app.css";
import { iniciarApp } from "./inicio";
import { registrarServiceWorker } from "./pwa";

/** Lê #convite=... e tira o fragmento da barra de endereço. */
function lerConvite(): string | null {
  const convite = new URLSearchParams(location.hash.slice(1)).get("convite");
  if (location.hash) {
    history.replaceState(null, "", location.pathname + location.search);
  }
  return convite || null;
}

const raiz = document.getElementById("raiz");
if (raiz === null) {
  throw new Error("#raiz ausente no index.html");
}

void iniciarApp(raiz, lerConvite());

registrarServiceWorker();
