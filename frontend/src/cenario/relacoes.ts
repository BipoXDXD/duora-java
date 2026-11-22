// Frases de relação da revelação: duas peças colocadas perto uma da outra ganham uma frase do cenário.

import type { Geometria } from "../dominio/dados";
import type { PecaNoLugar, Relacao } from "./tipos";

const MAXIMO_DE_FRASES = 2;

/**
 * Frases dos pares (sem ordem) cujos lugares distam no máximo `raioPerto` metros no cena.json,
 * na ordem das relações, até duas. Peça fora da cena ou lugar sem ponto não forma par.
 */
export function frasesDeRelacao(
  relacoes: readonly Relacao[],
  raioPerto: number,
  g: Geometria,
  colocadas: readonly PecaNoLugar[],
): string[] {
  const lugarDa = new Map(colocadas.map((c) => [c.peca, c.lugar]));
  const frases: string[] = [];
  for (const { pecas, perto } of relacoes) {
    const a = ponto(g, lugarDa.get(pecas[0]));
    const b = ponto(g, lugarDa.get(pecas[1]));
    if (a && b && Math.hypot(a.x - b.x, a.y - b.y) / g.pxPorMetro <= raioPerto) {
      frases.push(perto);
      if (frases.length === MAXIMO_DE_FRASES) {
        break;
      }
    }
  }
  return frases;
}

function ponto(g: Geometria, lugar: string | undefined): { x: number; y: number } | undefined {
  return lugar !== undefined && Object.hasOwn(g.lugares, lugar) ? g.lugares[lugar] : undefined;
}
