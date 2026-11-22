import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Convite } from "./Convite";

const LINK = "http://127.0.0.1/#convite=abc";

/** Clipboard API que aceita ou recusa a cópia, e o execCommand de navegadores antigos. */
function prepararCopia({ clipboard, execCommand }: { clipboard: boolean; execCommand: boolean }) {
  const writeText = vi.fn(() => (clipboard ? Promise.resolve() : Promise.reject(new Error("sem permissão"))));
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
  Object.defineProperty(document, "execCommand", { value: vi.fn(() => execCommand), configurable: true });
  return { writeText };
}

async function copiar(ehToque = false) {
  const aoCopiar = vi.fn();
  render(<Convite link={LINK} aoCopiar={aoCopiar} ehToque={ehToque} />);
  await userEvent.click(screen.getByRole("button", { name: "Copiar convite" }));
  await vi.waitFor(() => {
    expect(aoCopiar).toHaveBeenCalledOnce();
  });
  return aoCopiar;
}

afterEach(() => {
  Reflect.deleteProperty(navigator, "clipboard");
  Reflect.deleteProperty(document, "execCommand");
});

describe("Convite", () => {
  it("copia pela Clipboard API e avisa para abrir em outra janela", async () => {
    const { writeText } = prepararCopia({ clipboard: true, execCommand: false });

    const aoCopiar = await copiar();

    expect(writeText).toHaveBeenCalledWith(LINK);
    expect(aoCopiar).toHaveBeenCalledWith("Convite copiado. Abra-o em outra janela anônima ou em outro navegador.");
  });

  it("sem a Clipboard API, seleciona o link e copia pelo execCommand", async () => {
    prepararCopia({ clipboard: false, execCommand: true });

    const aoCopiar = await copiar();

    expect(aoCopiar).toHaveBeenCalledWith("Convite copiado.");
    expect(screen.getByLabelText("Link para a outra pessoa")).toHaveFocus();
  });

  it("sem nenhum jeito de copiar, deixa o link selecionado e diz como copiar", async () => {
    prepararCopia({ clipboard: false, execCommand: false });

    const aoCopiar = await copiar();

    expect(aoCopiar).toHaveBeenCalledWith("O link está selecionado. Copie com Ctrl+C (ou ⌘+C).");
    expect(screen.getByLabelText("Link para a outra pessoa")).toHaveFocus();
  });

  it("fora do toque, a dica ensina a testar em outra janela", () => {
    render(<Convite link={LINK} aoCopiar={vi.fn()} ehToque={false} />);

    expect(screen.getByLabelText("Link para a outra pessoa")).toHaveAccessibleDescription(
      "Para testar sozinho, abra o link em uma janela anônima ou em outro navegador: cada janela é uma pessoa. O convite vale para uma entrada.",
    );
  });

  it("no toque, a dica e o aviso de cópia não falam de janela", async () => {
    prepararCopia({ clipboard: true, execCommand: false });

    const aoCopiar = await copiar(true);

    expect(aoCopiar).toHaveBeenCalledWith("Convite copiado.");
    expect(screen.getByLabelText("Link para a outra pessoa")).toHaveAccessibleDescription(
      "O convite vale para uma entrada.",
    );
  });
});
