import "./estilos/app.css";
import { lerConvite } from "./convite";
import { iniciarApp } from "./inicio";
import { registrarServiceWorker } from "./pwa";

const raiz = document.getElementById("raiz");
if (raiz === null) {
  throw new Error("#raiz ausente no index.html");
}

void iniciarApp(raiz, lerConvite(location, history));

registrarServiceWorker();
