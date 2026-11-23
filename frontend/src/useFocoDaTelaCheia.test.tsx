import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { useFocoDaTelaCheia } from "./useFocoDaTelaCheia";

/** O esqueleto que importa ao foco: topo com o Sair, cena, painel e a folha do resultado. */
function montarApp(): void {
  document.body.innerHTML = `
    <header class="topo"><h1>Antes da primeira estrela</h1><button id="sair">Sair</button></header>
    <section class="cena"><button data-peca="lanterna">Lanterna</button></section>
    <aside class="painel">
      <button id="som">Som</button>
      <section id="resultado"><h2 id="resultado-titulo" tabindex="-1">A noite</h2></section>
    </aside>`;
}

function focar(seletor: string): void {
  document.querySelector<HTMLElement>(seletor)?.focus();
}

const focado = () => document.activeElement;

describe("useFocoDaTelaCheia", () => {
  beforeEach(montarApp);

  afterEach(() => {
    document.body.replaceChildren();
  });

  it("o foco num controle do painel vai para o título do resultado", () => {
    focar("#som");

    renderHook(() => {
      useFocoDaTelaCheia(true);
    });

    expect(focado()?.id).toBe("resultado-titulo");
  });

  it("o foco na cena fica onde está", () => {
    focar("[data-peca=lanterna]");

    renderHook(() => {
      useFocoDaTelaCheia(true);
    });

    expect(focado()?.getAttribute("data-peca")).toBe("lanterna");
  });

  it("o foco no Sair fica no Sair: o topo continua à vista na tela cheia", () => {
    focar("#sair");

    renderHook(() => {
      useFocoDaTelaCheia(true);
    });

    expect(focado()?.id).toBe("sair");
  });

  it("fora da tela cheia, o foco não se move", () => {
    focar("#som");

    renderHook(() => {
      useFocoDaTelaCheia(false);
    });

    expect(focado()?.id).toBe("som");
  });
});
