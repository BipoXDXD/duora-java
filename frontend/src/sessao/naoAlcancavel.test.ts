import { expect, it } from "vitest";
import { naoAlcancavel } from "./naoAlcancavel";

it("lança com o valor que escapou do switch", () => {
  const escapou = "desconhecida" as never;

  expect(() => naoAlcancavel(escapou)).toThrow('Caso não tratado: "desconhecida"');
});
