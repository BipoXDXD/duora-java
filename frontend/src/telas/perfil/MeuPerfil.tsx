import type { ReactElement } from "react";
import { TITULO_DA_CASCA } from "../../componentes/estilos";
import { MEU_PERFIL } from "./textos";

/** Esqueleto da tela (a F4 o substitui): o título, para a rota e o chunk existirem. */
export function MeuPerfil(): ReactElement {
  return (
    <>
      <title>{MEU_PERFIL.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{MEU_PERFIL.titulo}</h1>
    </>
  );
}
