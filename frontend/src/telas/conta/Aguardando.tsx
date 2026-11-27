import type { ReactElement } from "react";
import { Link } from "react-router";
import { LINK_DE_ACAO, TITULO_DA_CASCA } from "../../componentes/estilos";
import { AGUARDANDO } from "./textos";

/**
 * Cadastro concluído, verificação de idade pendente. Não há atalho que pule a verificação (D-A):
 * a tela só diz o próximo passo e leva à conta.
 */
export function Aguardando(): ReactElement {
  return (
    <>
      <title>{AGUARDANDO.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{AGUARDANDO.titulo}</h1>
      <p className="mt-(--e-4) mb-0 text-texto-2 text-[1.125rem] leading-[1.5]">{AGUARDANDO.proximoPasso}</p>
      <Link to="/conta" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
        {AGUARDANDO.minhaConta}
      </Link>
    </>
  );
}
