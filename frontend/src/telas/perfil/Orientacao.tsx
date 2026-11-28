import type { ReactElement } from "react";
import { TITULO_DA_CASCA } from "../../componentes/estilos";
import { ORIENTACAO } from "./textos";

/** Esqueleto da tela (a F9 o substitui): o título, para a rota e o chunk existirem. */
export function Orientacao(): ReactElement {
  return (
    <>
      <title>{ORIENTACAO.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{ORIENTACAO.titulo}</h1>
    </>
  );
}
