/**
 * O `Character.isWhitespace` do Java: os espaços Unicode (Zs, Zl, Zp) menos os não quebráveis
 * (U+00A0, U+2007, U+202F), mais U+0009–U+000D e U+001C–U+001F. Todos cabem numa unidade de UTF-16.
 */
function ehEspacoDoJava(codigo: number): boolean {
  return (
    (codigo >= 0x09 && codigo <= 0x0d) ||
    (codigo >= 0x1c && codigo <= 0x20) ||
    codigo === 0x1680 ||
    (codigo >= 0x2000 && codigo <= 0x200a && codigo !== 0x2007) ||
    codigo === 0x2028 ||
    codigo === 0x2029 ||
    codigo === 0x205f ||
    codigo === 0x3000
  );
}

/**
 * Tira os espaços das pontas como o `String.strip()` do servidor, para a tela contar, conferir e
 * enviar o mesmo texto que ele mede. O `trim` do JavaScript difere: tira U+00A0 e U+FEFF, e deixa
 * U+001C–U+001F.
 */
export function aparar(texto: string): string {
  let inicio = 0;
  let fim = texto.length;
  while (inicio < fim && ehEspacoDoJava(texto.charCodeAt(inicio))) {
    inicio += 1;
  }
  while (fim > inicio && ehEspacoDoJava(texto.charCodeAt(fim - 1))) {
    fim -= 1;
  }
  return texto.slice(inicio, fim);
}
