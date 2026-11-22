import { describe, expect, it } from "vitest";
import { criarAviso } from "./aviso";

/** Mutações childList no #aviso, como o AcessibilidadeFocoIT conta os anúncios. */
async function mutacoesDe(el: HTMLElement, agir: () => void): Promise<MutationRecord[]> {
  const registros: MutationRecord[] = [];
  const observador = new MutationObserver((lista) => registros.push(...lista));
  observador.observe(el, { childList: true, subtree: true, characterData: true });
  agir();
  await Promise.resolve();
  registros.push(...observador.takeRecords());
  observador.disconnect();
  return registros;
}

describe("criarAviso", () => {
  it("mesmoTextoDuasVezesGeraDuasMutacoes", async () => {
    const el = document.createElement("p");
    const aviso = criarAviso(el);

    const registros = await mutacoesDe(el, () => {
      aviso.avisar("Conexão de volta.");
      aviso.avisar("Conexão de volta.");
    });

    expect(registros.filter((r) => r.type === "childList")).toHaveLength(2);
    expect(el.textContent).toBe("Conexão de volta.");
  });

  it("marca o tipo, info por padrão", () => {
    const el = document.createElement("p");
    const aviso = criarAviso(el);

    aviso.avisar("Falhou.", "erro");
    const tipoDoErro = el.dataset.tipo;
    aviso.avisar("Tudo certo.");

    expect([tipoDoErro, el.dataset.tipo]).toEqual(["erro", "info"]);
  });

  it("detalheMudaSemTrocarOTexto", () => {
    const el = document.createElement("p");
    const aviso = criarAviso(el);

    aviso.avisarComDetalhe("Sem conexão com o servidor.", "Nova tentativa em 2 s.", "erro");
    const texto = el.firstChild;
    aviso.avisarComDetalhe("Sem conexão com o servidor.", "Nova tentativa em 4 s.", "erro");

    expect(el.firstChild).toBe(texto);
    expect(el.textContent).toBe("Sem conexão com o servidor. Nova tentativa em 4 s.");
    expect(el.querySelector("[data-detalhe]")).toHaveAttribute("aria-hidden", "true");
  });

  it("detalhe com outro texto fixo troca o texto", () => {
    const el = document.createElement("p");
    const aviso = criarAviso(el);

    aviso.avisarComDetalhe("Sem conexão com o servidor.", "Nova tentativa em 2 s.");
    const texto = el.firstChild;
    aviso.avisarComDetalhe("Outro problema.", "Nova tentativa em 2 s.");

    expect(el.firstChild).not.toBe(texto);
    expect(el.textContent).toBe("Outro problema. Nova tentativa em 2 s.");
  });
});
