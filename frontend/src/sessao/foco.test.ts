import { beforeEach, describe, expect, it } from "vitest";
import { destinoDoFoco, levarFocoSeSumiu, visivel } from "./foco";

// O jsdom não faz layout: getClientRects() vem sempre vazio. Os elementos "na tela" ganham um retângulo.
const UM_RETANGULO = [new DOMRect(0, 0, 10, 10)] as unknown as DOMRectList;

function mostrar(...ids: string[]): void {
  for (const id of ids) {
    const el = document.getElementById(id);
    if (el === null) {
      throw new Error(`#${id} ausente`);
    }
    el.getClientRects = () => UM_RETANGULO;
  }
}

const porId = (id: string): HTMLElement => {
  const el = document.getElementById(id);
  if (el === null) {
    throw new Error(`#${id} ausente`);
  }
  return el;
};

beforeEach(() => {
  document.body.innerHTML = `
    <input id="link-convite">
    <div id="pecas"><button id="lanterna" data-abertura>Acender sua lanterna</button></div>
    <ul id="inventario"><li><button id="item">Telescópio</button></li></ul>
    <button id="pronto">Pronto</button>
    <h2 id="resultado-titulo" tabindex="-1">Resultado</h2>
    <button id="som">Som</button>
    <button id="outro">Outro</button>`;
});

describe("visivel", () => {
  it("conectado e com retângulo", () => {
    mostrar("som");

    expect(visivel(porId("som"))).toBe(true);
  });

  it("sem retângulo (escondido)", () => {
    expect(visivel(porId("som"))).toBe(false);
  });

  it("fora do documento", () => {
    const solto = document.createElement("button");
    solto.getClientRects = () => UM_RETANGULO;

    expect(visivel(solto)).toBe(false);
  });
});

describe("destinoDoFoco", () => {
  it("montagem: o primeiro item do inventário", () => {
    mostrar("item", "pronto");

    expect(destinoDoFoco("MONTANDO", document)).toBe(porId("item"));
  });

  it("focoVaiParaALanternaDuranteAbertura", () => {
    mostrar("lanterna", "item", "pronto");

    expect(destinoDoFoco("MONTANDO", document)).toBe(porId("lanterna"));
  });

  it("montagem sem item visível: o Pronto", () => {
    mostrar("pronto");

    expect(destinoDoFoco("MONTANDO", document)).toBe(porId("pronto"));
  });

  it("revelado sem resultado visível: o som", () => {
    mostrar("som");

    expect(destinoDoFoco("REVELADO", document)).toBe(porId("som"));
  });

  it("nenhum candidato visível", () => {
    expect(destinoDoFoco("AGUARDANDO_PAR", document)).toBeUndefined();
  });
});

describe("levarFocoSeSumiu", () => {
  it("o elemento focado sumiu: vai ao destino da fase", () => {
    mostrar("link-convite", "som");
    const sumido = porId("pronto");
    sumido.focus();

    levarFocoSeSumiu(sumido, false, "AGUARDANDO_PAR", document);

    expect(document.activeElement).toBe(porId("link-convite"));
  });

  it("o foco segue visível no mesmo controle: não mexe", () => {
    mostrar("outro", "item");
    porId("outro").focus();

    levarFocoSeSumiu(porId("outro"), true, "MONTANDO", document);

    expect(document.activeElement).toBe(porId("outro"));
  });

  it("foco no body e troca de fase: vai ao destino", () => {
    mostrar("resultado-titulo", "som");

    levarFocoSeSumiu(document.body, true, "REVELADO", document);

    expect(document.activeElement).toBe(porId("resultado-titulo"));
  });

  it("foco no body sem troca de fase: não mexe", () => {
    mostrar("item");

    levarFocoSeSumiu(document.body, false, "MONTANDO", document);

    expect(document.activeElement).toBe(document.body);
  });

  it("o foco já foi devolvido a outro controle visível: não mexe", () => {
    mostrar("outro", "item");
    const sumido = porId("pronto");
    porId("outro").focus();

    levarFocoSeSumiu(sumido, false, "MONTANDO", document);

    expect(document.activeElement).toBe(porId("outro"));
  });
});
