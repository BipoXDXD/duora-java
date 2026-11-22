// Provisório: só prova o build. A Task 7 troca este arquivo pelo App do terraço.
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./estilos/app.css";

const raiz = document.getElementById("raiz");
if (raiz === null) {
  throw new Error("#raiz ausente no index.html");
}

createRoot(raiz).render(
  <StrictMode>
    <p>Duora</p>
  </StrictMode>,
);
