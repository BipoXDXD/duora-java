// Cliente das inscrições de Web Push (Etapa 4) sobre o transporte de http.ts. Sem a chave pública
// (push desligado no servidor), os avisos ficam indisponíveis e o resto do app segue igual.

import * as v from "valibot";
import { ErroHttp, requisitarHttp } from "./http";

const EsquemaChavePublica = v.strictObject({ chave: v.pipe(v.string(), v.nonEmpty()) });

/** O corpo do PUT /api/push/inscricao, já no formato do contrato. */
export interface InscricaoDoNavegador {
  endpoint: string;
  chaves: { p256dh: string; auth: string };
}

/** A chave VAPID pública em base64url; null quando o push está indisponível (404). */
export async function obterChavePublica(): Promise<string | null> {
  try {
    const resposta = await requisitarHttp("GET", "/push/chave-publica");
    const resultado = v.safeParse(EsquemaChavePublica, resposta.dados);
    if (!resultado.success) {
      throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
    }
    return resultado.output.chave;
  } catch (erro) {
    if (erro instanceof ErroHttp && erro.status === 404) {
      return null;
    }
    throw erro;
  }
}

/** Manda só os campos do contrato; um endpoint de outra conta passa para esta. */
export async function inscrever({ endpoint, chaves }: InscricaoDoNavegador): Promise<void> {
  await requisitarHttp("PUT", "/push/inscricao", {
    corpo: { endpoint, chaves: { p256dh: chaves.p256dh, auth: chaves.auth } },
  });
}

export async function removerInscricao(endpoint: string): Promise<void> {
  await requisitarHttp("DELETE", "/push/inscricao", { corpo: { endpoint } });
}
