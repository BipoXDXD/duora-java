// Tipos e medidas da arte da cena; os textos e os nomes de arquivo vêm do cenário (cenario/).

/**
 * Largura do render (família do cenário) para a tela: a menor das variantes do cena.json que cobre
 * larguraCss × dpr, ou a maior quando nenhuma cobre.
 */
export function escolherVariante(larguraCss: number, dpr: number, variantes: readonly number[]): number {
  const ordenadas = [...variantes].sort((a, b) => a - b);
  const alvo = larguraCss * dpr;
  const escolhida = ordenadas.find((v) => v >= alvo) ?? ordenadas.at(-1);
  if (escolhida === undefined) {
    throw new Error("Nenhuma variante de render informada");
  }
  return escolhida;
}

/** Uma medida do cena.json em porcentagem do total (palco ou sprite), para o style.left/top/width. */
export const pct = (valor: number, total: number) => `${((valor / total) * 100).toFixed(3)}%`;

/** Miniaturas do inventário têm 56 px: a família menor basta em qualquer densidade. */
export const LARGURA_MINIATURA = 900;

export type Momento = "crepusculo" | "noite";

export interface Ponto {
  x: number;
  y: number;
}

export interface MedidasSprite {
  largura: number;
  altura: number;
  ancoraX: number;
  ancoraY: number;
}

/** Geometria do cena.json: palco, escala, lugares e medidas de cada sprite nos dois momentos. */
export interface Geometria {
  largura: number;
  altura: number;
  pxPorMetro: number;
  variantes: number[];
  lugares: Record<string, Ponto>;
  props: Record<string, Record<Momento, MedidasSprite>>;
}
