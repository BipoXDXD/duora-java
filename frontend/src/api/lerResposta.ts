// Valida o corpo de uma resposta da API com um esquema valibot. Fora do formato vira ErroHttp
// RESPOSTA_INVALIDA, sem o corpo junto (o corpo pode ter dado pessoal e não vai para logs nem telas).

import * as v from "valibot";
import { ErroHttp } from "./http";

/** `status` é o da resposta lida e só vai no erro. */
export function lerResposta<S extends v.GenericSchema>(esquema: S, status: number, dados: unknown): v.InferOutput<S> {
  const resultado = v.safeParse(esquema, dados);
  if (!resultado.success) {
    throw new ErroHttp({ status, codigo: "RESPOSTA_INVALIDA" });
  }
  return resultado.output;
}
