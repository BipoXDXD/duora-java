import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState, type ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";
import { DialogoDeConfirmacao } from "./DialogoDeConfirmacao";

interface PropsDoCenario {
  enviando?: boolean;
  aoConfirmar?: () => void;
  /** Espião chamado junto com o fechamento pelo estado. */
  aoCancelar?: () => void;
}

/** Um botão de origem e o diálogo, com o estado de aberto na mão de quem usa, como na tela. */
function Cenario({
  enviando = false,
  aoConfirmar = () => undefined,
  aoCancelar = () => undefined,
}: PropsDoCenario): ReactElement {
  const [aberto, setAberto] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setAberto(true);
        }}
      >
        Abrir
      </button>
      <DialogoDeConfirmacao
        aberto={aberto}
        titulo="Excluir sua conta?"
        texto="Apagamos seus dados pessoais agora. Não dá para desfazer."
        acao="Excluir conta"
        aoConfirmar={aoConfirmar}
        aoCancelar={() => {
          aoCancelar();
          setAberto(false);
        }}
        enviando={enviando}
      />
    </>
  );
}

async function abrir(props: PropsDoCenario = {}) {
  const usuario = userEvent.setup();
  render(<Cenario {...props} />);
  await usuario.click(screen.getByRole("button", { name: "Abrir" }));
  const dialogo = screen.getByRole("dialog", { name: "Excluir sua conta?" });
  return { usuario, dialogo };
}

describe("DialogoDeConfirmacao", () => {
  it("fechadoNaoAparece", () => {
    render(<Cenario />);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("abertoTemTituloETexto", async () => {
    const { dialogo } = await abrir();

    expect(dialogo).toHaveAccessibleDescription("Apagamos seus dados pessoais agora. Não dá para desfazer.");
    expect(dialogo).toHaveAttribute("open");
  });

  it("focoIniciaEmCancelar", async () => {
    const { dialogo } = await abrir();

    expect(within(dialogo).getByRole("button", { name: "Cancelar" })).toHaveFocus();
  });

  it("escCancela", async () => {
    const { usuario } = await abrir();

    await usuario.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("cancelarFecha", async () => {
    const { usuario, dialogo } = await abrir();

    await usuario.click(within(dialogo).getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("focoVoltaAoBotaoDeOrigem", async () => {
    const { usuario, dialogo } = await abrir();

    await usuario.click(within(dialogo).getByRole("button", { name: "Cancelar" }));

    expect(screen.getByRole("button", { name: "Abrir" })).toHaveFocus();
  });

  it("acaoChamaAoConfirmar", async () => {
    const aoConfirmar = vi.fn<() => void>();
    const { usuario, dialogo } = await abrir({ aoConfirmar });

    await usuario.click(within(dialogo).getByRole("button", { name: "Excluir conta" }));

    expect(aoConfirmar).toHaveBeenCalledOnce();
  });

  it("enviandoDeixaAcaoIndisponivel", async () => {
    const aoConfirmar = vi.fn<() => void>();
    const { usuario, dialogo } = await abrir({ enviando: true, aoConfirmar });
    const acao = within(dialogo).getByRole("button", { name: "Excluir conta" });

    await usuario.click(acao);

    expect(acao).toHaveAttribute("aria-disabled", "true");
    expect(aoConfirmar).not.toHaveBeenCalled();
  });

  it("enviandoIgnoraOCancelar", async () => {
    const aoCancelar = vi.fn<() => void>();
    const { usuario, dialogo } = await abrir({ enviando: true, aoCancelar });
    const cancelar = within(dialogo).getByRole("button", { name: "Cancelar" });

    await usuario.click(cancelar);

    expect(cancelar).toHaveAttribute("aria-disabled", "true");
    expect(aoCancelar).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog", { name: "Excluir sua conta?" })).toBeInTheDocument();
  });

  it("enviandoMarcaODialogoOcupado", async () => {
    const { dialogo } = await abrir({ enviando: true });

    expect(dialogo).toHaveAttribute("aria-busy", "true");
  });

  it("paradoNaoMarcaODialogoOcupado", async () => {
    const { dialogo } = await abrir();

    expect(dialogo).not.toHaveAttribute("aria-busy");
  });

  /** O Chrome fecha sem perguntar no segundo Esc seguido, mesmo com o preventDefault. */
  it("fechamentoForcadoChamaAoCancelar", async () => {
    const aoCancelar = vi.fn<() => void>();
    const { dialogo } = await abrir({ aoCancelar });

    act(() => {
      (dialogo as HTMLDialogElement).close();
    });

    expect(aoCancelar).toHaveBeenCalledOnce();
    expect(screen.getByRole("button", { name: "Abrir" })).toHaveFocus();
  });

  it("enviandoIgnoraOEsc", async () => {
    const { usuario } = await abrir({ enviando: true });

    await usuario.keyboard("{Escape}");

    expect(screen.getByRole("dialog", { name: "Excluir sua conta?" })).toBeInTheDocument();
  });
});
