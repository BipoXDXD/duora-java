import cenarioJson from "../../../src/main/resources/static/assets/cenarios/terraco/cenario.json?raw";
import cenaJson from "../../../src/main/resources/static/assets/cenarios/terraco/cena/cena.json?raw";
import { lerCenario } from "../cenario/ler";
import type { CenarioFront } from "../cenario/tipos";

/** Os dois JSON do terraço decodificados de novo a cada chamada: o teste pode alterá-los à vontade. */
export function jsonsDoTerraco(): { cenario: unknown; geometria: unknown } {
  return { cenario: JSON.parse(cenarioJson) as unknown, geometria: JSON.parse(cenaJson) as unknown };
}

/** O terraço lido dos arquivos servidos, como o front o recebe do carregarCenario. */
export function cenarioDoTerraco(): CenarioFront {
  const { cenario, geometria } = jsonsDoTerraco();
  return lerCenario(cenario, geometria, "terraco");
}

/** O bloco de abertura que o terraço vai declarar (lanternas de mão nos fundos de cada lado). */
export const ABERTURA_DO_TERRACO = {
  tipo: "acender-junto",
  sprite: "lanterna",
  escala: 0.5,
  lugares: { A: "L2", B: "R2" },
} as const;

/** Os JSON do terraço com a abertura declarada no cenario.json. */
export function jsonsDoTerracoComAbertura(): { cenario: unknown; geometria: unknown } {
  const { cenario, geometria } = jsonsDoTerraco();
  return { cenario: { ...(cenario as object), abertura: structuredClone(ABERTURA_DO_TERRACO) }, geometria };
}

/** O terraço com a abertura declarada. */
export function cenarioDoTerracoComAbertura(): CenarioFront {
  const { cenario, geometria } = jsonsDoTerracoComAbertura();
  return lerCenario(cenario, geometria, "terraco");
}
