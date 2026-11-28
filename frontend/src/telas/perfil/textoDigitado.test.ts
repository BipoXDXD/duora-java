import { describe, expect, it } from "vitest";
import { semEspacosNasPontas, temCaractereProibido, temCaractereVisivel, textoCurtoCabe } from "./textoDigitado";

// Os mesmos casos de DescricaoCurtaTest e BioTest no servidor.
describe("textoCurtoCabe", () => {
  it.each(["\u00A0", "\u3000", "\u200B", "\uFEFF", "\u00AD", "\u3164", "\u3000\u3164"])(
    "soInvisiveisRecusados %#",
    (texto) => {
      expect(textoCurtoCabe(texto, 40)).toBe(false);
    },
  );

  it.each(["Lu\u200Ba", "Lu\uFEFFa", "Lu\u00ADa", "Lu\u202Ea", "Lu\u2066a", "Lu\u200Ea"])(
    "formatacaoNoMeioRecusada %#",
    (texto) => {
      expect(textoCurtoCabe(texto, 40)).toBe(false);
    },
  );

  it("zwjDoEmojiAceito", () => {
    expect(textoCurtoCabe("\u{1F469}\u200D\u{1F467}", 40)).toBe(true);
  });

  it("acimaDoMaximoRecusado", () => {
    expect(textoCurtoCabe("a".repeat(21), 20)).toBe(false);
  });
});

describe("semEspacosNasPontas", () => {
  it.each(["\u00A0Lua\u00A0", "\u3000Lua\u2007", "\u202FLua\u205F", " Lua\n"])("espacosUnicodeCortados %#", (texto) => {
    expect(semEspacosNasPontas(texto)).toBe("Lua");
  });

  it("bomNaPontaFicaParaSerRecusado", () => {
    expect(semEspacosNasPontas("\uFEFFLua")).toBe("\uFEFFLua");
  });
});

describe("bio", () => {
  it.each(["oi\u202Eoi", "oi\u2066oi", "oi\u200Boi", "oi\uFEFF", "oi\u2028oi"])("formatacaoRecusada %#", (texto) => {
    expect(temCaractereProibido(texto, true)).toBe(true);
  });

  it("quebraDeLinhaAceita", () => {
    expect(temCaractereProibido("oi\nlinha", true)).toBe(false);
  });

  it("espacoSemQuebraNasPontasCortado", () => {
    expect(semEspacosNasPontas("\u00A0oi\nlinha\u3000")).toBe("oi\nlinha");
  });
});

describe("temCaractereVisivel", () => {
  it("letraAoLadoDoPreenchimentoEhVisivel", () => {
    expect(temCaractereVisivel("\u3164a")).toBe(true);
  });
});
