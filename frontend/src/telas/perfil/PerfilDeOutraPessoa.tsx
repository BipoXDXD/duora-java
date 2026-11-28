import type { ReactElement } from "react";
import { useLoaderData } from "react-router";
import type { PerfilPublico } from "../../api/perfil";
import { TITULO_DA_CASCA } from "../../componentes/estilos";
import { PERFIL_DE_OUTRA_PESSOA } from "./textos";

/** Esqueleto da tela (a F6 o substitui): o apelido no título, ou o perfil indisponível. */
export function PerfilDeOutraPessoa(): ReactElement {
  const perfil = useLoaderData<PerfilPublico | null>();
  if (perfil === null) {
    return (
      <>
        <title>{PERFIL_DE_OUTRA_PESSOA.tituloDaAbaIndisponivel}</title>
        <h1 className={TITULO_DA_CASCA}>{PERFIL_DE_OUTRA_PESSOA.indisponivel}</h1>
      </>
    );
  }
  return (
    <>
      <title>{PERFIL_DE_OUTRA_PESSOA.tituloDaAba(perfil.apelido)}</title>
      <h1 className={TITULO_DA_CASCA}>{perfil.apelido}</h1>
    </>
  );
}
