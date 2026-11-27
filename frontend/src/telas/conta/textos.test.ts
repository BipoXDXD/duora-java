import { describe, expect, it } from "vitest";
import { textoDeEspera } from "./textos";

describe("textoDeEspera", () => {
  it("retryAfter60Mostra1Minuto", () => {
    expect(textoDeEspera(60)).toBe("Muitas tentativas. Tente de novo em 1 minuto.");
  });

  it("retryAfter61Mostra2Minutos", () => {
    expect(textoDeEspera(61)).toBe("Muitas tentativas. Tente de novo em 2 minutos.");
  });

  it("menosDeUmMinutoViraUmMinuto", () => {
    expect(textoDeEspera(0)).toBe("Muitas tentativas. Tente de novo em 1 minuto.");
  });
});
