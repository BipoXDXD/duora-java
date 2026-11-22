// Textos e arte de cada peça e lugar. As regras ficam no servidor; aqui só há apresentação.

import type { Assento } from "./visao";

export interface DadosPeca {
  nome: string;
  artigo: string;
  assento: Assento;
  zona: "centro" | "lateral";
  obrigatoria?: true;
}

export interface DadosLugar {
  nome: string;
}

export const PECAS: Readonly<Record<string, DadosPeca>> = {
  telescopio: { nome: "Telescópio", artigo: "o telescópio", assento: "A", zona: "centro", obrigatoria: true },
  manta: { nome: "Manta", artigo: "a manta", assento: "A", zona: "lateral" },
  radio: { nome: "Rádio", artigo: "o rádio", assento: "A", zona: "lateral" },
  banco: { nome: "Banco", artigo: "o banco", assento: "B", zona: "centro", obrigatoria: true },
  lanterna: { nome: "Lanterna", artigo: "a lanterna", assento: "B", zona: "lateral" },
  garrafa: { nome: "Garrafa térmica", artigo: "a garrafa térmica", assento: "B", zona: "lateral" },
};

export const LUGARES: Readonly<Record<string, DadosLugar>> = {
  C1: { nome: "centro, à esquerda" },
  C2: { nome: "centro, à direita" },
  L1: { nome: "fundo, à esquerda" },
  L2: { nome: "frente, à esquerda" },
  R1: { nome: "fundo, à direita" },
  R2: { nome: "frente, à direita" },
};

/** Dados de uma peça da API; um id desconhecido é contrato quebrado, não um caso a tratar. */
export function dadosDaPeca(peca: string): DadosPeca {
  const dados = PECAS[peca];
  if (dados === undefined) {
    throw new Error(`Peça desconhecida: ${peca}`);
  }
  return dados;
}

/** Dados de um lugar da API; um id desconhecido é contrato quebrado, não um caso a tratar. */
export function dadosDoLugar(lugar: string): DadosLugar {
  const dados = LUGARES[lugar];
  if (dados === undefined) {
    throw new Error(`Lugar desconhecido: ${lugar}`);
  }
  return dados;
}

/** Nome do sprite: o telescópio aponta para o lado do céu do seu lugar. */
export function sprite(peca: string, lugar: string): string {
  if (peca === "telescopio") {
    return lugar === "C2" ? "telescopio-dir" : "telescopio-esq";
  }
  return peca;
}

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

export function urlSprite(nome: string, momento: Momento, largura: number): string {
  return `/assets/cenarios/terraco/cena/${nome}-${momento}-${String(largura)}.webp`;
}

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

export async function carregarCena(): Promise<Geometria> {
  const resposta = await fetch("/assets/cenarios/terraco/cena/cena.json");
  if (!resposta.ok) {
    throw new Error(`cena.json indisponível (${String(resposta.status)})`);
  }
  return (await resposta.json()) as Geometria;
}
