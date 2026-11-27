// Cliente da API de conta sobre o transporte de http.ts. Só a casca de conta importa este módulo:
// o terraço não paga esse JS.

import { ErroHttp, requisitarHttp } from "./http";

export interface ConfiguracaoDaConta {
  chaveDoSiteTurnstile: string;
}

function ehConfiguracao(dados: unknown): dados is ConfiguracaoDaConta {
  if (typeof dados !== "object" || dados === null) {
    return false;
  }
  const chave = (dados as Partial<Record<keyof ConfiguracaoDaConta, unknown>>).chaveDoSiteTurnstile;
  return typeof chave === "string" && chave !== "";
}

/** Configuração pública das telas de conta (C5), como a chave do site do Turnstile. */
export async function obterConfiguracao(): Promise<ConfiguracaoDaConta> {
  const resposta = await requisitarHttp("GET", "/conta/configuracao");
  if (!ehConfiguracao(resposta.dados)) {
    throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  return { chaveDoSiteTurnstile: resposta.dados.chaveDoSiteTurnstile };
}
