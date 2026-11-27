import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Topo } from "./Topo";

function renderizar(mudancas: Partial<Parameters<typeof Topo>[0]> = {}) {
  const props = {
    mostrarSair: true,
    mostrarLinkDaConta: true,
    nomeParceiro: "Bruno",
    confirmando: true,
    enviandoSaida: false,
    aoAlternarSaida: vi.fn(),
    aoContinuar: vi.fn(),
    aoEncerrar: vi.fn(),
    ...mudancas,
  };
  render(<Topo {...props} />);
  return props;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Topo", () => {
  it("linkDaContaApareceSoSemOSair", async () => {
    const fetchFalso = vi.fn<typeof fetch>(() => Promise.resolve(new Response(null, { status: 204 })));
    vi.stubGlobal("fetch", fetchFalso);

    renderizar({ mostrarSair: false, confirmando: false });
    expect(await screen.findByRole("link", { name: "Entrar" })).toHaveAttribute("href", "/entrar");
    cleanup();
    fetchFalso.mockClear();

    renderizar({ confirmando: false });
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(screen.getByRole("button", { name: "Sair" })).toBeVisible();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(fetchFalso).not.toHaveBeenCalled();
  });

  it("Esc no grupo da confirmação fecha a confirmação sem subir até o documento", async () => {
    const noDocumento = vi.fn();
    document.addEventListener("keydown", noDocumento);
    const props = renderizar();

    screen.getByRole("button", { name: "Continuar no terraço" }).focus();
    await userEvent.keyboard("{Escape}");
    document.removeEventListener("keydown", noDocumento);

    expect(props.aoAlternarSaida).toHaveBeenCalledOnce();
    expect(noDocumento).not.toHaveBeenCalled();
  });

  it("Continuar no terraço durante o envio da saída não faz nada", async () => {
    const props = renderizar({ enviandoSaida: true });

    await userEvent.click(screen.getByRole("button", { name: "Continuar no terraço" }));
    screen.getByRole("button", { name: "Continuar no terraço" }).focus();
    await userEvent.keyboard("{Escape}");

    expect(props.aoContinuar).not.toHaveBeenCalled();
    expect(props.aoAlternarSaida).not.toHaveBeenCalled();
  });

  it("a confirmação aberta nomeia o parceiro e o Sair aponta para ela", () => {
    renderizar();

    const grupo = screen.getByRole("group", { name: "Isso encerra o terraço para Bruno também." });
    const sair = screen.getByRole("button", { name: "Sair" });
    expect(sair).toHaveAttribute("aria-expanded", "true");
    expect(sair).toHaveAttribute("aria-controls", grupo.id);
    expect(screen.getByRole("button", { name: "Encerrar para as duas pessoas" })).toBeInTheDocument();
  });

  it("fechada, a confirmação some e o Sair não aponta para nada", () => {
    renderizar({ confirmando: false });

    const sair = screen.getByRole("button", { name: "Sair" });
    expect(sair).toHaveAttribute("aria-expanded", "false");
    expect(sair).not.toHaveAttribute("aria-controls");
    expect(screen.queryByRole("group")).not.toBeInTheDocument();
  });

  it("durante o envio, o Encerrar diz Enviando… e ignora o clique", async () => {
    const props = renderizar({ enviandoSaida: true });

    const encerrar = screen.getByRole("button", { name: "Enviando…" });
    await userEvent.click(encerrar);

    expect(encerrar).toHaveAttribute("aria-disabled", "true");
    expect(props.aoEncerrar).not.toHaveBeenCalled();
  });
});
