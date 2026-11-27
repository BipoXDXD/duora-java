// Cliente da API pública dos documentos legais (L1), sem sessão. Usa só o fetch de http.ts: o rodapé
// do terraço importa este módulo, e o JS inicial não leva TanStack Query.

import { ErroHttp, requisitarHttp } from "./http";

export type DocumentoLegal = "termos" | "privacidade";

/** Texto atual de um documento: a versão (AAAA-MM-DD) e o HTML que o servidor gera do Markdown. */
export interface TextoLegal {
  versao: string;
  html: string;
}

const VERSAO = /^\d{4}-\d{2}-\d{2}$/;

export interface VersoesLegais {
  termos: { versao: string };
  privacidade: { versao: string };
  contato: string;
  encarregado: { nome: string; email: string };
}

function objeto(valor: unknown): Record<string, unknown> | null {
  return typeof valor === "object" && valor !== null ? (valor as Record<string, unknown>) : null;
}

function texto(valor: unknown): string | null {
  return typeof valor === "string" && valor !== "" ? valor : null;
}

function lerVersoes(dados: unknown): VersoesLegais | null {
  const raiz = objeto(dados);
  const versaoTermos = texto(objeto(raiz?.termos)?.versao);
  const versaoPrivacidade = texto(objeto(raiz?.privacidade)?.versao);
  const contato = texto(raiz?.contato);
  const encarregado = objeto(raiz?.encarregado);
  const nome = texto(encarregado?.nome);
  const email = texto(encarregado?.email);
  if (versaoTermos === null || versaoPrivacidade === null || contato === null || nome === null || email === null) {
    return null;
  }
  return {
    termos: { versao: versaoTermos },
    privacidade: { versao: versaoPrivacidade },
    contato,
    encarregado: { nome, email },
  };
}

/** Versões atuais dos Termos e da Política, mais os contatos. O cadastro aceita exatamente estas versões. */
export async function obterVersoesLegais(): Promise<VersoesLegais> {
  const resposta = await requisitarHttp("GET", "/legal/versoes");
  const versoes = lerVersoes(resposta.dados);
  if (versoes === null) {
    throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  return versoes;
}

function lerDocumento(dados: unknown): TextoLegal | null {
  const raiz = objeto(dados);
  const versao = texto(raiz?.versao);
  const html = texto(raiz?.html);
  if (versao === null || !VERSAO.test(versao) || html === null) {
    return null;
  }
  return { versao, html };
}

/**
 * Versão e HTML atuais dos Termos ou da Política. O HTML vem do Markdown versionado no repositório,
 * com HTML bruto escapado e links saneados no servidor; nunca carrega entrada de usuário.
 */
export async function obterDocumentoLegal(documento: DocumentoLegal): Promise<TextoLegal> {
  const resposta = await requisitarHttp("GET", `/legal/${documento}`);
  const lido = lerDocumento(resposta.dados);
  if (lido === null) {
    throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  return lido;
}
