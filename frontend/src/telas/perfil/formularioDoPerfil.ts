// Regras do formulário de Meu perfil, iguais às do servidor (D1): textos sem espaços nas pontas,
// contados por code point, com a regra de caracteres de textoDigitado.

import type { EdicaoDoPerfil, MeuPerfil } from "../../api/perfil";
import { contarCaracteres } from "./contar";
import { semEspacosNasPontas, temCaractereProibido, temCaractereVisivel, textoCurtoCabe } from "./textoDigitado";
import { ERROS_DO_PERFIL } from "./textos";

export type ErrosDoPerfil = Partial<Record<"apelido" | "descricaoPronomes" | "intencoes" | "bio", string>>;

const APELIDO_MAXIMO = 20;
const DESCRICAO_DOS_PRONOMES_MAXIMA = 20;
export const BIO_MAXIMA = 280;

/** A bio como o servidor a grava: CRLF vira LF e as pontas perdem os espaços. */
export function bioNormalizada(bio: string): string {
  return semEspacosNasPontas(bio.replace(/\r\n/g, "\n"));
}

/** Os erros da edição, por campo; vazio quando dá para enviar. */
export function validar(e: EdicaoDoPerfil): ErrosDoPerfil {
  const erros: ErrosDoPerfil = {};
  const apelido = semEspacosNasPontas(e.apelido);
  if (!temCaractereVisivel(apelido)) {
    erros.apelido = ERROS_DO_PERFIL.semApelido;
  } else if (temCaractereProibido(apelido)) {
    erros.apelido = ERROS_DO_PERFIL.apelidoInvisivel;
  } else if (contarCaracteres(apelido) > APELIDO_MAXIMO) {
    erros.apelido = ERROS_DO_PERFIL.apelidoLongo;
  }
  if (e.pronomes === "OUTROS" && !textoCurtoCabe(e.descricaoPronomes ?? "", DESCRICAO_DOS_PRONOMES_MAXIMA)) {
    erros.descricaoPronomes = ERROS_DO_PERFIL.descricaoDosPronomes;
  }
  if (e.intencoes.length === 0) {
    erros.intencoes = ERROS_DO_PERFIL.semIntencao;
  }
  const bio = bioNormalizada(e.bio);
  if (temCaractereProibido(bio, true)) {
    erros.bio = ERROS_DO_PERFIL.bioInvisivel;
  } else if (contarCaracteres(bio) > BIO_MAXIMA) {
    erros.bio = ERROS_DO_PERFIL.bioLonga;
  }
  return erros;
}

/** O formulário a partir do perfil salvo; sem perfil, o apelido começa vazio. */
export function deMeuPerfil(p: MeuPerfil): EdicaoDoPerfil {
  return {
    versao: p.versao,
    apelido: p.apelido ?? "",
    pronomes: p.pronomes,
    descricaoPronomes: p.descricaoPronomes,
    intencoes: p.intencoes,
    bio: p.bio,
  };
}

/** Salvar esta edição tira Namoro de quem tinha (o servidor apaga os dados de namoro junto). */
export function tiraNamoro(salvo: MeuPerfil, e: EdicaoDoPerfil): boolean {
  return salvo.intencoes.includes("NAMORO") && !e.intencoes.includes("NAMORO");
}
