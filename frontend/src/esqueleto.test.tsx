import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import indexHtml from "../index.html?raw";
import { Moldura } from "./componentes/Moldura";
import { Topo } from "./componentes/Topo";

/** O esqueleto estático do index.html, que pinta antes do JS chegar. */
function esqueleto(): Document {
  return new DOMParser().parseFromString(indexHtml, "text/html");
}

function classes(raiz: ParentNode, seletor: string): string | undefined {
  return raiz.querySelector(seletor)?.getAttribute("class") ?? undefined;
}

describe("esqueleto da primeira pintura", () => {
  it("tem a moldura, o topo e o título com as classes do app, para o céu e o título já ficarem no lugar", () => {
    const { container } = render(
      <Moldura momento="crepusculo" confirmandoSaida={false} ceuPintado={false}>
        <Topo
          mostrarSair={false}
          nomeParceiro={null}
          confirmando={false}
          enviandoSaida={false}
          aoAlternarSaida={() => undefined}
          aoContinuar={() => undefined}
          aoEncerrar={() => undefined}
        />
      </Moldura>,
    );
    const estatico = esqueleto();

    for (const seletor of [".app", ".topo", ".titulo"]) {
      expect(classes(estatico, `#raiz ${seletor}`), seletor).toBe(classes(container, seletor));
    }
    // O documento do DOMParser não tem janela: o jest-dom não o aceita, então os atributos vão à mão.
    expect(estatico.querySelector("#raiz .app")?.getAttribute("data-momento")).toBe("crepusculo");
    expect(estatico.querySelector("#raiz .app > section.cena > .ceu")).not.toBeNull();
  });

  it("não tem estilo inline, que a CSP bloqueia", () => {
    expect(esqueleto().querySelectorAll("[style], style, script:not([src])")).toHaveLength(0);
  });
});
