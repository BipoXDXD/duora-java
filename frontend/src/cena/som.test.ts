import { describe, expect, it } from "vitest";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { arquivosDoSom } from "./som";

describe("arquivosDoSom", () => {
  it("volumes e URLs vêm do cenário", () => {
    expect(arquivosDoSom(cenarioDoTerraco())).toEqual({
      ambiente: { url: "/assets/cenarios/terraco/audio/ambiente.mp3", volume: 0.35 },
      meteoro: { url: "/assets/cenarios/terraco/audio/meteoro.mp3", volume: 0.5 },
      revelacao: { url: "/assets/cenarios/terraco/audio/revelacao.mp3", volume: 0.6 },
    });
  });
});
