// Validação do apelido no navegador, antes de ir ao servidor (que valida de novo).

const CONTROLE = /\p{Cc}/u;
const TAMANHO_MAXIMO = 20;

/** O texto do erro, ou "" quando o apelido serve. Conta caracteres, não unidades UTF-16. */
export function validarApelido(apelido: string): string {
  // Pontos de código, como o servidor conta: um emoji simples vale 1.
  const tamanho = Array.from(apelido).length;
  if (tamanho === 0) {
    return "Escreva um apelido.";
  }
  if (tamanho > TAMANHO_MAXIMO) {
    return "Use no máximo 20 caracteres.";
  }
  if (CONTROLE.test(apelido)) {
    return "Use só letras, números e símbolos comuns.";
  }
  return "";
}
