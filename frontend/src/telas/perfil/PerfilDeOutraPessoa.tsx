import type { ReactElement } from "react";
import { Link, useLoaderData } from "react-router";
import type { PerfilPublico } from "../../api/perfil";
import { CartaoDoPerfil } from "../../componentes/CartaoDoPerfil";
import { LINK_DE_ACAO, LINK_DE_SAIDA, TITULO_DA_CASCA } from "../../componentes/estilos";
import { PERFIL_DE_OUTRA_PESSOA as TEXTOS } from "./textos";

/**
 * O perfil de outra pessoa, como o servidor entrega: a foto borrada (nunca a nítida) ou a silhueta,
 * os pronomes e o selo. Inexistente, sem permissão e link malformado dão a mesma tela de perfil
 * indisponível, sem dizer qual foi o caso. O terraço não é rota da casca: "Voltar ao terraço"
 * carrega a página inteira.
 */
export function PerfilDeOutraPessoa(): ReactElement {
  const perfil = useLoaderData<PerfilPublico | null>();
  if (perfil === null) {
    return (
      <>
        <title>{TEXTOS.tituloDaAbaIndisponivel}</title>
        <h1 className={TITULO_DA_CASCA}>{TEXTOS.indisponivel}</h1>
        <p className="mt-(--e-4) mb-0 text-texto-2 text-[1.125rem] leading-[1.5]">{TEXTOS.explicacaoDoIndisponivel}</p>
        <Link to="/conta" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
          {TEXTOS.minhaConta}
        </Link>
      </>
    );
  }
  return (
    <>
      <title>{TEXTOS.tituloDaAba(perfil.apelido)}</title>
      <h1 className={`${TITULO_DA_CASCA} [overflow-wrap:anywhere]`}>{perfil.apelido}</h1>
      <div className="mt-(--e-6)">
        <CartaoDoPerfil perfil={perfil} carregamento="eager" />
      </div>
      <ul className="mt-(--e-7) mb-0 p-0 list-none flex flex-wrap gap-x-(--e-5)">
        <li>
          <Link to="/conta" className={LINK_DE_SAIDA}>
            {TEXTOS.minhaConta}
          </Link>
        </li>
        <li>
          <a href="/" className={LINK_DE_SAIDA}>
            {TEXTOS.voltarAoTerraco}
          </a>
        </li>
      </ul>
    </>
  );
}
