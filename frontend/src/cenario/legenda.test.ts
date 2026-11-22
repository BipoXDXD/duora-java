import { describe, expect, it } from "vitest";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { CENARIO_MINIMO } from "../teste/cenarioMinimo";
import { interpolar } from "./legenda";
import { ErroCenario } from "./tipos";

const NOMES = { A: "Ana", B: "Bruno" };

describe("legenda", () => {
  it("legenda do terraço igual a hoje", () => {
    const resultado = { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "leste" };

    expect(cenarioDoTerraco().legenda(resultado, NOMES)).toEqual({
      titulo: "Uma noite de manta e lanterna",
      texto:
        "O telescópio de Ana aponta para o lado leste do céu, e é por ali que os meteoros passam. " +
        "Ana trouxe a manta; Bruno, o banco e a lanterna.",
    });
  });

  it("rádio e garrafa", () => {
    const resultado = { secundariaA: "radio", secundariaB: "garrafa", ladoTelescopio: "oeste" };

    expect(cenarioDoTerraco().legenda(resultado, NOMES).texto).toBe(
      "O telescópio de Ana aponta para o lado oeste do céu, e é por ali que os meteoros passam. " +
        "Ana trouxe o rádio; Bruno, o banco e a garrafa térmica.",
    );
  });

  it("legenda do cenário mínimo", () => {
    expect(CENARIO_MINIMO.legenda({ juntos: "sim" }, NOMES)).toEqual({
      titulo: "Juntos: sim",
      texto: "Ana e Bruno terminaram.",
    });
  });
});

describe("interpolar", () => {
  it("marcador desconhecido lança", () => {
    const interpolarDesconhecido = () => interpolar("{xyz}", { secundariaA: "manta" }, cenarioDoTerraco());

    expect(interpolarDesconhecido).toThrow(ErroCenario);
    expect(interpolarDesconhecido).toThrow("marcador desconhecido: {xyz}");
  });

  it("campo desconhecido lança", () => {
    const interpolarCampo = () => interpolar("{secundariaA.cor}", { secundariaA: "manta" }, cenarioDoTerraco());

    expect(interpolarCampo).toThrow(ErroCenario);
    expect(interpolarCampo).toThrow("marcador desconhecido: {secundariaA.cor}");
  });
});
