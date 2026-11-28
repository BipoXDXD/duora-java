/** Conta por code point, como o servidor: um emoji vale 1, e a quebra de linha também. */
export function contarCaracteres(texto: string): number {
  return Array.from(texto).length;
}
