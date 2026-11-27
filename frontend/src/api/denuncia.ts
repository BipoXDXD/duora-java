// Canal público de denúncia (C12), sem sessão. Só a tela /denunciar importa este módulo.

import { ErroHttp, requisitarHttp } from "./http";

export type MotivoDaDenuncia = "PARECE_MENOR" | "ABUSO_INFANTIL" | "IMAGEM_INTIMA" | "ASSEDIO" | "GOLPE" | "OUTRO";

export interface DenunciaPublica {
  motivo: MotivoDaDenuncia;
  descricao: string;
  referencia?: string;
  emailContato?: string;
  tokenVerificacao: string;
}

function lerMensagem(dados: unknown): string | null {
  if (typeof dados !== "object" || dados === null) {
    return null;
  }
  const mensagem = (dados as { mensagem?: unknown }).mensagem;
  return typeof mensagem === "string" && mensagem !== "" ? mensagem : null;
}

/**
 * Registra a denúncia. A recusa de um campo chega como 422 DENUNCIA_PUBLICA_INVALIDA, com a mensagem
 * do campo; o limite por IP, como 429 com Retry-After. Os opcionais ausentes ficam fora do corpo.
 */
export async function enviarDenuncia(denuncia: DenunciaPublica): Promise<{ mensagem: string }> {
  const resposta = await requisitarHttp("POST", "/denuncias-publicas", { corpo: denuncia });
  const mensagem = lerMensagem(resposta.dados);
  if (mensagem === null) {
    throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  return { mensagem };
}
