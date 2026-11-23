const CONTRACOES: [artigo: string, contraido: string][] = [
  ["o ", "pelo "],
  ["a ", "pela "],
  ["os ", "pelos "],
  ["as ", "pelas "],
];

/** "por" + o nome com artigo, contraído quando o artigo é definido: "o rádio" vira "pelo rádio". */
export function porArtigo(nomeComArtigo: string): string {
  const contracao = CONTRACOES.find(([inicio]) => nomeComArtigo.startsWith(inicio));
  return contracao ? contracao[1] + nomeComArtigo.slice(contracao[0].length) : `por ${nomeComArtigo}`;
}
