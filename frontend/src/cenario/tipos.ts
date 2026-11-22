// Cenário do front: apresentação (cenario.json) e geometria (cena.json) já conferidas por ler.ts.
// Nenhum consumidor lê o JSON cru; tudo passa pelos métodos de CenarioFront.

import type { Geometria, Momento } from "../dominio/dados";
import type { Assento } from "../dominio/visao";

/** cenario.json ou cena.json ausente, inválido ou incoerente com o outro. */
export class ErroCenario extends Error {
  override readonly name = "ErroCenario";
}

export type NomeSom = "ambiente" | "meteoro" | "revelacao";

/** Efeito noturno de uma peça na revelação; medidas em metros, convertidas pelo pxPorMetro. */
export type Efeito =
  | { readonly tipo: "brilho-piso"; readonly largura: number }
  | { readonly tipo: "halo"; readonly alturaProjetada: number; readonly largura: number };

export interface PecaDoCenario {
  readonly nome: string;
  readonly artigo: string;
  readonly assento: Assento;
  readonly zona: string;
  readonly obrigatoria: boolean;
}

export interface LugarDoCenario {
  readonly nome: string;
}

/** Textos de uma zona; o checklist traz {nome} no lugar do nome da peça (vazio: sem item). */
export interface ZonaDoCenario {
  readonly descricao: string;
  readonly instrucao: string;
  readonly checklist: string;
}

export interface Legenda {
  readonly titulo: string;
  readonly texto: string;
}

/** Uma peça já colocada e o lugar onde está. */
export interface PecaNoLugar {
  readonly peca: string;
  readonly lugar: string;
}

/** Par de peças (sem ordem) e a frase de quando ficam perto uma da outra. */
export interface Relacao {
  readonly pecas: readonly [string, string];
  readonly perto: string;
}

export interface CenarioFront {
  readonly id: string;
  readonly nome: string;
  readonly geometria: Geometria;
  readonly fundo: string;
  /** Um id desconhecido é contrato quebrado: lança Error. */
  peca(id: string): PecaDoCenario;
  /** Um id desconhecido é contrato quebrado: lança Error. */
  lugar(id: string): LugarDoCenario;
  zona(id: string): ZonaDoCenario;
  /** Nome do sprite da peça no lugar; null = fora da cena (miniatura do inventário). */
  sprite(peca: string, lugar: string | null): string;
  urlSprite(nome: string, momento: Momento, largura: number): string;
  urlCeu(momento: Momento, largura: number): string;
  som(nome: NomeSom): { readonly url: string; readonly volume: number };
  /** URL de um som de efeito pelo nome-base (ex.: "radio-notas"), na mesma pasta do som(). */
  audio(nome: string): string;
  /** Até duas frases dos pares de peças colocadas perto, na ordem do cenario.json. */
  frasesDeRelacao(colocadas: readonly PecaNoLugar[]): readonly string[];
  revelacao(peca: string): readonly Efeito[];
  legenda(resultado: Readonly<Record<string, string>>, nomes: Readonly<Record<Assento, string>>): Legenda;
}
