import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { SessaoDeCaptura } from "../../api/verificacao";
import DetectorFalso from "./DetectorFalso";

const SESSAO: SessaoDeCaptura = {
  sessaoId: "sessao-falsa",
  regiao: "us-east-1",
  modo: "falso",
  credencial: {
    accessKeyId: "ASIAFALSO",
    secretAccessKey: "segredo-falso",
    sessionToken: "token-falso",
    expiration: new Date("2026-11-27T12:00:00Z"),
  },
  desvioDoRelogioMs: undefined,
};

function renderizar() {
  const props = { sessao: SESSAO, aoConcluir: vi.fn(), aoFalhar: vi.fn(), aoCancelar: vi.fn() };
  render(<DetectorFalso {...props} />);
  return props;
}

describe("DetectorFalso", () => {
  it("simularCapturaConclui", async () => {
    const usuario = userEvent.setup();
    const { aoConcluir, aoFalhar, aoCancelar } = renderizar();

    expect(screen.getByText("Modo de teste: nenhuma câmera é usada.")).toBeInTheDocument();
    await usuario.click(screen.getByRole("button", { name: "Simular captura" }));

    expect(aoConcluir).toHaveBeenCalledOnce();
    expect(aoFalhar).not.toHaveBeenCalled();
    expect(aoCancelar).not.toHaveBeenCalled();
  });

  it("cancelarChamaAoCancelar", async () => {
    const usuario = userEvent.setup();
    const { aoConcluir, aoCancelar } = renderizar();

    await usuario.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(aoCancelar).toHaveBeenCalledOnce();
    expect(aoConcluir).not.toHaveBeenCalled();
  });

  // D-F2: nada global que um script da página pudesse usar para pular a câmera.
  it("naoTocaNoWindow", async () => {
    const usuario = userEvent.setup();
    const antes = new Set(Object.getOwnPropertyNames(window));

    renderizar();
    await usuario.click(screen.getByRole("button", { name: "Simular captura" }));

    expect(Object.getOwnPropertyNames(window).filter((nome) => !antes.has(nome))).toEqual([]);
  });
});
