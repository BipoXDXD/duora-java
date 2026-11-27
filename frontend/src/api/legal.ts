// Cliente da API pública dos documentos legais (L1), sem sessão. Só a casca de conta importa este módulo.

import { ErroHttp, requisitarHttp } from "./http";

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
