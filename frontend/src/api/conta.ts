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

export interface RespostaDaConfirmacao {
  cadastroConcluido: boolean;
}

function ehRespostaDaConfirmacao(dados: unknown): dados is RespostaDaConfirmacao {
  if (typeof dados !== "object" || dados === null) {
    return false;
  }
  return typeof (dados as Partial<Record<keyof RespostaDaConfirmacao, unknown>>).cadastroConcluido === "boolean";
}

/**
 * Troca o token do link mágico por uma sessão (C6); o cookie chega no Set-Cookie da resposta. O token
 * é de uso único: quem chama garante um POST só por clique.
 */
export async function confirmarEntrada(token: string): Promise<RespostaDaConfirmacao> {
  const resposta = await requisitarHttp("POST", "/conta/confirmar", { corpo: { token } });
  if (!ehRespostaDaConfirmacao(resposta.dados)) {
    throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  return { cadastroConcluido: resposta.dados.cadastroConcluido };
}

export interface DadosDoCadastro {
  /** AAAA-MM-DD, como o input type=date entrega. */
  dataDeNascimento: string;
  versaoTermos: string;
  versaoPrivacidade: string;
  aceito: true;
}

export interface RespostaDoCadastro {
  cadastroConcluido: false;
  proximoPasso: "VERIFICACAO";
}

function ehRespostaDoCadastro(dados: unknown): dados is RespostaDoCadastro {
  if (typeof dados !== "object" || dados === null) {
    return false;
  }
  const campos = dados as Partial<Record<keyof RespostaDoCadastro, unknown>>;
  return campos.cadastroConcluido === false && campos.proximoPasso === "VERIFICACAO";
}

/**
 * Conclui o cadastro da conta da sessão (C7) com as versões legais lidas do servidor. A recusa por
 * idade (422 MENOR_DE_IDADE) já chega com a conta excluída e o cookie expirado.
 */
export async function concluirCadastro(dados: DadosDoCadastro): Promise<RespostaDoCadastro> {
  const resposta = await requisitarHttp("POST", "/conta/cadastro", { corpo: dados });
  if (!ehRespostaDoCadastro(resposta.dados)) {
    throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  return { cadastroConcluido: false, proximoPasso: "VERIFICACAO" };
}

/** Encerra a sessão deste navegador (C4): o 204 apaga a sessão e expira o cookie. */
export async function sairDaConta(): Promise<void> {
  await requisitarHttp("POST", "/conta/sair");
}

/**
 * Exclui a conta da sessão (C8) com a confirmação explícita. O 204 já chega com as sessões revogadas
 * e o cookie expirado. Só pedidos concorrentes respondem 204 os dois; uma nova tentativa depois de uma
 * exclusão que chegou ao servidor recebe 401 NAO_AUTENTICADO (a sessão já foi revogada).
 */
export async function excluirConta(): Promise<void> {
  await requisitarHttp("POST", "/conta/exclusao", { corpo: { confirmo: true } });
}
