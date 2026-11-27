import { LINK_DE_ACAO, TITULO_DA_CASCA } from "../componentes/estilos";

/**
 * Endereço fora das rotas da casca. O link volta ao terraço com recarga completa (um <a> comum, não
 * o Link do router): "/" é o app do encontro, que a casca não monta.
 */
export function NaoEncontrada() {
  return (
    <>
      <title>Página não encontrada · Duora</title>
      <h1 className={TITULO_DA_CASCA}>Página não encontrada</h1>
      <p className="mt-(--e-4) mb-0 text-texto-2 text-[1.125rem] leading-[1.5]">
        Este endereço não leva a nenhuma página do Duora. Confira se o link chegou completo.
      </p>
      <a href="/" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
        Voltar ao terraço
      </a>
    </>
  );
}
