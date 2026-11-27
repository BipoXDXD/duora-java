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

export interface RespostaDoPedidoDeLink {
  mensagem: string;
}

function ehRespostaDoPedido(dados: unknown): dados is RespostaDoPedidoDeLink {
  if (typeof dados !== "object" || dados === null) {
    return false;
  }
  const mensagem = (dados as Partial<Record<keyof RespostaDoPedidoDeLink, unknown>>).mensagem;
  return typeof mensagem === "string" && mensagem !== "";
}

/**
 * Pede o link mágico (C5); a resposta é a mesma exista ou não a conta. A `chave` vai no
 * Idempotency-Key: repetir com a mesma chave e o mesmo corpo não gasta outro token do Turnstile.
 */
export async function pedirLink(
  email: string,
  tokenVerificacao: string,
  chave: string,
): Promise<RespostaDoPedidoDeLink> {
  const resposta = await requisitarHttp("POST", "/conta/entrar", {
    corpo: { email, tokenVerificacao },
    cabecalhos: { "Idempotency-Key": chave },
  });
  if (!ehRespostaDoPedido(resposta.dados)) {
    throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  return { mensagem: resposta.dados.mensagem };
}
