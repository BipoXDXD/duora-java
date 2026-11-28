// Regras do formulário de namoro, iguais às do servidor (D2): a descrição sem espaços nas pontas,
// contada por code point, e só com Prefiro descrever.

import type { DadosDeNamoro, EdicaoDoNamoro, Genero } from "../../api/perfil";
import { contarCaracteres } from "./contar";
import { ERROS_DO_NAMORO } from "./textos";

/** O formulário como a tela o guarda: sem gênero até a escolha, e a descrição como digitada. */
export interface EdicaoDoNamoroNaTela {
  genero: Genero | null;
  descricaoGenero: string;
  interesses: Genero[];
}

export type ErrosDoNamoro = Partial<Record<"genero" | "descricaoGenero" | "interesses", string>>;

const DESCRICAO_MAXIMA = 40;

/** Os erros da edição, por campo; vazio quando dá para enviar. */
export function validarNamoro(d: EdicaoDoNamoroNaTela): ErrosDoNamoro {
  const erros: ErrosDoNamoro = {};
  if (d.genero === null) {
    erros.genero = ERROS_DO_NAMORO.semGenero;
  } else if (d.genero === "OUTRO") {
    const tamanho = contarCaracteres(d.descricaoGenero.trim());
    if (tamanho < 1 || tamanho > DESCRICAO_MAXIMA) {
      erros.descricaoGenero = ERROS_DO_NAMORO.descricao;
    }
  }
  if (d.interesses.length === 0) {
    erros.interesses = ERROS_DO_NAMORO.semInteresse;
  }
  return erros;
}

/** O formulário a partir dos dados gravados; sem dados, tudo vazio. */
export function deDadosDeNamoro(d: DadosDeNamoro | null): EdicaoDoNamoroNaTela {
  return d === null
    ? { genero: null, descricaoGenero: "", interesses: [] }
    : { genero: d.genero, descricaoGenero: d.descricaoGenero ?? "", interesses: d.interesses };
}

/** O corpo do PUT: a descrição vai só com Prefiro descrever, e o consentimento sempre verdadeiro. */
export function paraEnvio(d: EdicaoDoNamoroNaTela & { genero: Genero }): EdicaoDoNamoro {
  return {
    genero: d.genero,
    descricaoGenero: d.genero === "OUTRO" ? d.descricaoGenero.trim() : null,
    interesses: d.interesses,
    consentimento: true,
  };
}
