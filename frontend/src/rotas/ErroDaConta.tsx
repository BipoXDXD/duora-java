import type { ReactElement } from "react";
import { useRevalidator, useRouteError } from "react-router";
import { ErroHttp } from "../api/http";
import { BOTAO_SECUNDARIO_SEM_MARGEM, LINK_DE_ACAO, TITULO_DA_CASCA } from "../componentes/estilos";
import { CONFIRMAR, ERRO_DA_CONTA } from "../telas/conta/textos";

const PARAGRAFO = "mt-(--e-4) mb-0 text-texto-2 text-[1.125rem] leading-[1.5]";

/**
 * Falha do guarda de uma rota que exige conta. A conta indisponível (403) tem a mensagem dela, sem
 * cair no fluxo do anônimo; o resto (rede, servidor) oferece outra tentativa do loader.
 */
export function ErroDaConta(): ReactElement {
  const erro = useRouteError();
  const revalidador = useRevalidator();

  if (erro instanceof ErroHttp && erro.codigo === "CONTA_INDISPONIVEL") {
    return (
      <>
        <title>{ERRO_DA_CONTA.tituloDaAbaIndisponivel}</title>
        <h1 className={TITULO_DA_CASCA}>{ERRO_DA_CONTA.tituloIndisponivel}</h1>
        <p className={PARAGRAFO}>{erro.mensagem ?? CONFIRMAR.contaIndisponivel}</p>
        <a href="/" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
          {ERRO_DA_CONTA.voltarAoTerraco}
        </a>
      </>
    );
  }
  return (
    <>
      <title>{ERRO_DA_CONTA.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{ERRO_DA_CONTA.titulo}</h1>
      <p className={PARAGRAFO}>{ERRO_DA_CONTA.falha}</p>
      <button
        type="button"
        className={`mt-(--e-6) ${BOTAO_SECUNDARIO_SEM_MARGEM}`}
        aria-disabled={revalidador.state === "loading" ? true : undefined}
        onClick={() => {
          if (revalidador.state === "idle") {
            void revalidador.revalidate();
          }
        }}
      >
        {ERRO_DA_CONTA.tentarDeNovo}
      </button>
    </>
  );
}
