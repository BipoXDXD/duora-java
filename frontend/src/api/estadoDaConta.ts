// Estado da conta da sessão, lido pelo terraço a cada carga do "/". Fica fora de conta.ts (só da
// casca) e usa o fetch de http.ts, que o terraço já carrega: sem TanStack Query no JS inicial.

import { ErroHttp, requisitarHttp } from "./http";

export type EstadoDaConta = "PENDENTE" | "AGUARDANDO_VERIFICACAO" | "ATIVA";

export interface Conta {
  estado: EstadoDaConta;
  cadastroConcluido: boolean;
  verificada: boolean;
}

/** Exaustivo: um estado novo no tipo obriga a listá-lo aqui. */
const ESTADOS: readonly string[] = Object.keys({
  PENDENTE: true,
  AGUARDANDO_VERIFICACAO: true,
  ATIVA: true,
} satisfies Record<EstadoDaConta, true>);

function ehConta(dados: unknown): dados is Conta {
  if (typeof dados !== "object" || dados === null) {
    return false;
  }
  const campos = dados as Partial<Record<keyof Conta, unknown>>;
  return (
    typeof campos.estado === "string" &&
    ESTADOS.includes(campos.estado) &&
    typeof campos.cadastroConcluido === "boolean" &&
    typeof campos.verificada === "boolean"
  );
}

/**
 * 200 → a conta; 204 → null (anônimo). Qualquer outro status rejeita com ErroHttp, inclusive 401,
 * que a rota nunca devolve. Nunca redireciona: quem chama decide o que mostrar.
 */
export async function obterConta(): Promise<Conta | null> {
  const resposta = await requisitarHttp("GET", "/conta");
  if (resposta.status === 204) {
    return null;
  }
  if (resposta.status !== 200 || !ehConta(resposta.dados)) {
    throw new ErroHttp({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  const { estado, cadastroConcluido, verificada } = resposta.dados;
  return { estado, cadastroConcluido, verificada };
}
