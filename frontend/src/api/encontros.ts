// Cliente do encontro privado por convite e do assento (Etapa 4) sobre o transporte de http.ts.
// Esquemas estritos: fora do formato vira ErroHttp RESPOSTA_INVALIDA, sem o corpo junto. Criar e
// entrar devolvem só JSON; o cookie do assento vem de pedirAssento.

import * as v from "valibot";
import { ErroHttp, requisitarHttp } from "./http";

const EsquemaCenario = v.strictObject({ id: v.string(), nome: v.string() });

const EsquemaCenarios = v.strictObject({ cenarios: v.array(EsquemaCenario) });

const EsquemaConviteCriado = v.strictObject({ encontroId: v.string(), convite: v.string(), cenario: v.string() });

const EsquemaEntradaPorConvite = v.strictObject({ encontroId: v.string(), cenario: v.string() });

export type CenarioDoConvite = v.InferOutput<typeof EsquemaCenario>;
export type ConviteCriado = v.InferOutput<typeof EsquemaConviteCriado>;
export type EntradaPorConvite = v.InferOutput<typeof EsquemaEntradaPorConvite>;

function ler<S extends v.GenericSchema>(esquema: S, status: number, dados: unknown): v.InferOutput<S> {
  const resultado = v.safeParse(esquema, dados);
  if (!resultado.success) {
    throw new ErroHttp({ status, codigo: "RESPOSTA_INVALIDA" });
  }
  return resultado.output;
}

export async function obterCenariosDoConvite(): Promise<CenarioDoConvite[]> {
  const resposta = await requisitarHttp("GET", "/encontros/privados/cenarios");
  return ler(EsquemaCenarios, resposta.status, resposta.dados).cenarios;
}

/** `cenario` nulo deixa o servidor sortear. A mesma `chave` repete a criação sem abrir outro convite. */
export async function criarConvite(cenario: string | null, chave: string): Promise<ConviteCriado> {
  const resposta = await requisitarHttp("POST", "/encontros/privados", {
    corpo: { cenario },
    cabecalhos: { "Idempotency-Key": chave },
  });
  return ler(EsquemaConviteCriado, resposta.status, resposta.dados);
}

/** A mesma `chave` repete a entrada sem gastar o convite de novo. */
export async function entrarPorConvite(convite: string, chave: string): Promise<EntradaPorConvite> {
  const resposta = await requisitarHttp("POST", "/encontros/privados/entrar", {
    corpo: { convite },
    cabecalhos: { "Idempotency-Key": chave },
  });
  return ler(EsquemaEntradaPorConvite, resposta.status, resposta.dados);
}

/** Pede o cookie duora_assento; a Visao do corpo é ignorada (a sala lê a sua). */
export async function pedirAssento(encontroId: string): Promise<void> {
  await requisitarHttp("POST", `/encontros/${encodeURIComponent(encontroId)}/assento`);
}
