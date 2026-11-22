import { lerCenario } from "./ler";
import { ErroCenario, type CenarioFront } from "./tipos";

/** O front joga só o terraço até a Etapa 4 trazer o cenário na visão (R13). */
export const CENARIO_PADRAO = "terraco";

/** Mesmo padrão dos ids de cenário no servidor: o id vira caminho de URL. */
const ID_VALIDO = /^[a-z0-9-]+$/;

/** Busca em paralelo o cenario.json e o cena.json do cenário `id` e os lê. */
export async function carregarCenario(id: string, buscar: typeof fetch = fetch): Promise<CenarioFront> {
  if (!ID_VALIDO.test(id)) {
    throw new ErroCenario(`id de cenário inválido: ${JSON.stringify(id)}`);
  }
  const base = `/assets/cenarios/${id}`;
  const [cenario, geometria] = await Promise.all([
    buscarJson(`${base}/cenario.json`, id, buscar),
    buscarJson(`${base}/cena/cena.json`, id, buscar),
  ]);
  return lerCenario(cenario, geometria, id);
}

async function buscarJson(url: string, id: string, buscar: typeof fetch): Promise<unknown> {
  const resposta = await buscar(url);
  if (!resposta.ok) {
    throw new ErroCenario(`cenário ${id} indisponível (${String(resposta.status)})`);
  }
  try {
    return (await resposta.json()) as unknown;
  } catch (erro) {
    throw new ErroCenario(`cenário ${id}: ${url} não é um JSON válido`, { cause: erro });
  }
}
