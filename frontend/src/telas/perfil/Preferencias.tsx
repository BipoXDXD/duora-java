import type { ReactElement } from "react";
import { TITULO_DA_CASCA } from "../../componentes/estilos";
import { PREFERENCIAS } from "./textos";

/** Esqueleto da tela (a F7 o substitui): o título, para a rota e o chunk existirem. */
export function Preferencias(): ReactElement {
  return (
    <>
      <title>{PREFERENCIAS.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{PREFERENCIAS.titulo}</h1>
    </>
  );
}
