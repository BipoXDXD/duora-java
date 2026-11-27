import { describe, expect, it } from "vitest";
import * as textos from "./textos";

/** Todo texto fixo exportado, inclusive os de objetos aninhados. */
function textosFixos(valor: unknown): string[] {
  if (typeof valor === "string") {
    return [valor];
  }
  if (typeof valor === "object" && valor !== null) {
    return Object.values(valor).flatMap(textosFixos);
  }
  return [];
}

describe("textoDeEspera", () => {
  it("retryAfter60Mostra1Minuto", () => {
    expect(textos.textoDeEspera(60)).toBe("Muitas tentativas. Tente de novo em 1 minuto.");
  });

  it("retryAfter61Mostra2Minutos", () => {
    expect(textos.textoDeEspera(61)).toBe("Muitas tentativas. Tente de novo em 2 minutos.");
  });

  it("menosDeUmMinutoViraUmMinuto", () => {
    expect(textos.textoDeEspera(0)).toBe("Muitas tentativas. Tente de novo em 1 minuto.");
  });
});

describe("textos", () => {
  it("nenhumTextoPrometeEtapaFutura", () => {
    const fixos = textosFixos(textos);

    expect(fixos.length).toBeGreaterThan(0);
    expect(fixos.filter((texto) => /chega em breve/i.test(texto))).toEqual([]);
  });
});
