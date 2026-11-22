import { expectTypeOf, it } from "vitest";
import type { Momento } from "../dominio/dados";
import type { EstadoDaSessao, EstadoDaTela, Tela } from "./tipos";

// Conferido pelo tsc -b (o build e o lint tipado falham se o tipo divergir).
it("Tela é derivada das telas do estado", () => {
  expectTypeOf<Tela>().toEqualTypeOf<EstadoDaTela["tela"]>();
  expectTypeOf<Tela>().toEqualTypeOf<"carregando" | "entrada" | "convite" | "montagem" | "encerrado">();
});

it("o momento da sessão é o Momento dos sprites", () => {
  expectTypeOf<EstadoDaSessao["momento"]>().toEqualTypeOf<Momento>();
});
