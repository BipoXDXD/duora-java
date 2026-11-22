/** Fecha um switch exaustivo: o compilador recusa a chamada se algum caso da união ficou de fora. */
export function naoAlcancavel(x: never): never {
  throw new Error(`Caso não tratado: ${JSON.stringify(x)}`);
}
