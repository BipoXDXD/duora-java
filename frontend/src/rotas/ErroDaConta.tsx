import type { ReactElement } from "react";
import { useRevalidator, useRouteError } from "react-router";
import { ErroHttp } from "../api/http";
import { BOTAO_SECUNDARIO_SEM_MARGEM, LINK_DE_ACAO, TITULO_DA_CASCA } from "../componentes/estilos";
import { CONFIRMAR, ERRO_DA_CONTA } from "../telas/conta/textos";
import { MolduraDeConta } from "./LayoutDeConta";

const PARAGRAFO = "mt-(--e-4) mb-0 text-texto-2 text-[1.125rem] leading-[1.5]";

/**
 * Falha de qualquer rota da casca (o ErrorBoundary fica na raiz "/"): o guarda, o download da tela
 * ou o render dela. Toma o lugar do layout, então desenha a mesma moldura. A conta indisponível
 * (403) tem a mensagem dela, sem cair no fluxo do anônimo; o resto oferece outra tentativa
 * (revalidar refaz o loader e, se a tela não veio, o lazy).
 */
export function ErroDaConta(): ReactElement {
  const erro = useRouteError();
  const revalidador = useRevalidator();

  if (erro instanceof ErroHttp && erro.codigo === "CONTA_INDISPONIVEL") {
    return (
      <MolduraDeConta>
        <title>{ERRO_DA_CONTA.tituloDaAbaIndisponivel}</title>
        <h1 className={TITULO_DA_CASCA}>{ERRO_DA_CONTA.tituloIndisponivel}</h1>
        <p className={PARAGRAFO}>{erro.mensagem ?? CONFIRMAR.contaIndisponivel}</p>
        <a href="/" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
          {ERRO_DA_CONTA.voltarAoTerraco}
        </a>
      </MolduraDeConta>
    );
  }
  return (
    <MolduraDeConta>
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
    </MolduraDeConta>
  );
}
