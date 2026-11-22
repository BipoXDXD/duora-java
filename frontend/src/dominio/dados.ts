// Tipos e medidas da arte da cena; os textos e os nomes de arquivo vêm do cenário (cenario/).

/** Larguras renderizadas pelo cena.py; o cena.json traz a mesma lista em "variantes". */
export const VARIANTES = [900, 1800, 2700];

/**
 * Largura do render (família do terraço) para a tela: a menor variante que cobre
 * larguraCss × dpr, ou a maior quando nenhuma cobre.
 */
export function escolherVariante(larguraCss: number, dpr: number, variantes: readonly number[] = VARIANTES): number {
  const ordenadas = [...variantes].sort((a, b) => a - b);
  const alvo = larguraCss * dpr;
  const escolhida = ordenadas.find((v) => v >= alvo) ?? ordenadas.at(-1);
  if (escolhida === undefined) {
    throw new Error("Nenhuma variante de render informada");
  }
  return escolhida;
}

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
