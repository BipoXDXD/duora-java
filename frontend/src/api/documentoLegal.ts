// Texto dos documentos legais (L1), sem sessão. Separado de legal.ts para ficar fora do JS inicial:
// só a tela dos documentos, carregada sob demanda, importa este módulo.

import { ErroHttp, requisitarHttp } from "./http";

export type DocumentoLegal = "termos" | "privacidade";

/** Texto atual de um documento: a versão (AAAA-MM-DD) e o HTML que o servidor gera do Markdown. */
export interface TextoLegal {
  versao: string;
  html: string;
}

const VERSAO = /^\d{4}-\d{2}-\d{2}$/;

function lerDocumento(dados: unknown): TextoLegal | null {
  if (typeof dados !== "object" || dados === null) {
    return null;
  }
  const { versao, html } = dados as Record<string, unknown>;
  if (typeof versao !== "string" || !VERSAO.test(versao) || typeof html !== "string" || html === "") {
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
