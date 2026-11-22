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
