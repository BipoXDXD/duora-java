import type { ReactElement } from "react";
import { TITULO_DA_CASCA } from "../../componentes/estilos";
import { NAMORO } from "./textos";

/** Esqueleto da tela (a F8 o substitui): o título, para a rota e o chunk existirem. */
export function Namoro(): ReactElement {
  return (
    <>
      <title>{NAMORO.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{NAMORO.titulo}</h1>
    </>
  );
}
