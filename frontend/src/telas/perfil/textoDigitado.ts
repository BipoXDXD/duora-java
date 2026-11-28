// Regra de caracteres dos textos do perfil, igual à do servidor (DescricaoCurta e Bio): pontas sem
// nenhum espaço Unicode, nada de controle nem de formatação invisível (salvo o ZWJ dos emojis) e,
// nos textos curtos, ao menos um caractere visível.

import { contarCaracteres } from "./contar";

const ZWJ = "\u200D";
// Espaço Unicode (Z) e as quebras e tabulações ASCII, como DescricaoCurta.espaco no servidor.
const ESPACO = /[\p{Z}\t\n\v\f\r]/u;
const ESPACOS_NAS_PONTAS = /^[\p{Z}\t\n\v\f\r]+|[\p{Z}\t\n\v\f\r]+$/gu;
const CONTROLE = /\p{Cc}/u;
const FORMATACAO = /\p{Cf}/u;
const SEPARADORES = /[\u2028\u2029]/u;
/** Letras de preenchimento que a fonte desenha em branco. */
const PREENCHIMENTOS = /[\u115F\u1160\u3164\uFFA0\u2800]/u;

export function semEspacosNasPontas(texto: string): string {
  return texto.replace(ESPACOS_NAS_PONTAS, "");
}

function proibido(c: string): boolean {
  return CONTROLE.test(c) || (FORMATACAO.test(c) && c !== ZWJ) || SEPARADORES.test(c);
}

function visivel(c: string): boolean {
  return !ESPACO.test(c) && !proibido(c) && c !== ZWJ && !PREENCHIMENTOS.test(c);
}

/** Tem caractere que o servidor recusa; na bio, a quebra de linha vale. */
export function temCaractereProibido(texto: string, aceitaQuebra = false): boolean {
  return Array.from(texto).some((c) => proibido(c) && !(aceitaQuebra && c === "\n"));
}

/** Tem ao menos um caractere que aparece na tela. */
export function temCaractereVisivel(texto: string): boolean {
  return Array.from(texto).some(visivel);
}

/** O texto curto (apelido, Prefiro descrever) que o servidor aceita, já sem as pontas. */
export function textoCurtoCabe(texto: string, maximo: number): boolean {
  const limpo = semEspacosNasPontas(texto);
  return temCaractereVisivel(limpo) && !temCaractereProibido(limpo) && contarCaracteres(limpo) <= maximo;
}
