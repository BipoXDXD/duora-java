// Legenda da revelação: os modelos do cenario.json com marcadores trocados pelos valores do resultado.

import type { Assento } from "../dominio/visao";
import { ErroCenario, type CenarioFront, type Legenda, type PecaDoCenario } from "./tipos";

const MARCADOR = /\{([^{}]*)\}/g;

const CAMPOS: Readonly<Record<string, (peca: PecaDoCenario) => string>> = {
  nome: (peca) => peca.nome,
  nomeMinusculo: (peca) => peca.nome.toLowerCase(),
  artigo: (peca) => peca.artigo,
};

/**
 * Troca `{chave}` por `valores[chave]` e `{chave.campo}` pelo campo da peça `valores[chave]`
 * (`nome`, `nomeMinusculo` ou `artigo`). Chave ou campo desconhecido lança ErroCenario.
 */
export function interpolar(
  modelo: string,
  valores: Readonly<Record<string, string>>,
  c: Pick<CenarioFront, "peca">,
): string {
  return modelo.replace(MARCADOR, (marcador, conteudo: string) => {
    const [chave = "", campo, ...resto] = conteudo.split(".");
    const valor = entrada(valores, chave);
    if (valor === undefined || resto.length > 0) {
      throw new ErroCenario(`marcador desconhecido: ${marcador}`);
    }
    if (campo === undefined) {
      return valor;
    }
    const lerCampo = entrada(CAMPOS, campo);
    if (lerCampo === undefined) {
      throw new ErroCenario(`marcador desconhecido: ${marcador}`);
    }
    return lerCampo(c.peca(valor));
  });
}

/** Só chaves próprias: "toString" ou "__proto__" não são marcadores. */
function entrada<T>(registro: Readonly<Record<string, T>>, chave: string): T | undefined {
  return Object.hasOwn(registro, chave) ? registro[chave] : undefined;
}

/** Título e texto da legenda; além do resultado, os modelos podem usar `{nomeA}` e `{nomeB}`. */
export function legenda(
  modelos: Legenda,
  resultado: Readonly<Record<string, string>>,
  nomes: Readonly<Record<Assento, string>>,
  c: Pick<CenarioFront, "peca">,
): Legenda {
  const valores = { ...resultado, nomeA: nomes.A, nomeB: nomes.B };
  return { titulo: interpolar(modelos.titulo, valores, c), texto: interpolar(modelos.texto, valores, c) };
}
