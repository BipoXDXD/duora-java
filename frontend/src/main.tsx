import "./estilos/app.css";
import { despachar } from "./despachar";
import { iniciarApp } from "./inicio";
import { registrarServiceWorker } from "./pwa";

const raiz = document.getElementById("raiz");
if (raiz === null) {
  throw new Error("#raiz ausente no index.html");
}

despachar(location, history, raiz, {
  iniciarTerraco: iniciarApp,
  // Fora do JS inicial: o terraço não baixa React Router nem TanStack Query (chunk casca-de-conta).
  carregarCasca: () => import("./rotas/montar"),
});

registrarServiceWorker();
