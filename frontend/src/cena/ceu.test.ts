import { describe, expect, it } from "vitest";
import type { Momento } from "../dominio/dados";
import css from "../estilos/cena.css?raw";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { urlsDoCeu } from "./ceu";

const c = cenarioDoTerraco();

describe("céu", () => {
  it.each([
    [1, 1280],
    [2, 2560],
  ])("pré-carrega a largura do dpr %i", (dpr, largura) => {
    expect(urlsDoCeu(c, dpr)).toEqual([
      `/assets/cenarios/terraco/ceu/ceu-crepusculo-${String(largura)}.webp`,
      `/assets/cenarios/terraco/ceu/ceu-noite-${String(largura)}.webp`,
    ]);
  });

  it.each<[Momento, number]>([
    ["crepusculo", 1280],
    ["crepusculo", 2560],
    ["noite", 1280],
    ["noite", 2560],
  ])("cena.css usa as mesmas URLs do céu do terraço (%s, %i)", (momento, largura) => {
    expect(css).toContain(c.urlCeu(momento, largura));
  });
});
