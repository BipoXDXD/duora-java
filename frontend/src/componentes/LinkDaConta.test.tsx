import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Conta } from "../api/estadoDaConta";
import { ErroHttp } from "../api/http";
import { LinkDaConta } from "./LinkDaConta";

const ATIVA: Conta = { estado: "ATIVA", cadastroConcluido: true, verificada: false };

/** Deixa as promessas já resolvidas (e as atualizações de estado delas) acontecerem. */
async function esperarAResposta(): Promise<void> {
  await new Promise((resolver) => setTimeout(resolver, 0));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("LinkDaConta", () => {
  it("anonimoVeEntrar", async () => {
    render(<LinkDaConta obter={() => Promise.resolve(null)} />);

    const link = await screen.findByRole("link", { name: "Entrar" });

    expect(link).toHaveAttribute("href", "/entrar");
  });

  it("comContaVeMinhaConta", async () => {
    render(<LinkDaConta obter={() => Promise.resolve(ATIVA)} />);

    const link = await screen.findByRole("link", { name: "Minha conta" });

    expect(link).toHaveAttribute("href", "/conta");
  });

  it("erroNaoMostraLink", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    render(<LinkDaConta obter={() => Promise.reject(new ErroHttp({ status: 500, codigo: "SERVIDOR" }))} />);

    await esperarAResposta();

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it.each([
    ["500", new ErroHttp({ status: 500, codigo: "SERVIDOR" })],
    ["falha de rede", new ErroHttp({ status: 0, codigo: "REDE" })],
  ])("falhaInesperadaAvisaNoConsole (%s)", async (_caso, erro) => {
    const aviso = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    render(<LinkDaConta obter={() => Promise.reject(erro)} />);

    await esperarAResposta();

    expect(aviso).toHaveBeenCalledWith("Estado da conta indisponível:", erro);
  });

  it("contaIndisponivelNaoAvisa", async () => {
    const aviso = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const erro = new ErroHttp({ status: 403, codigo: "CONTA_INDISPONIVEL" });
    render(<LinkDaConta obter={() => Promise.reject(erro)} />);

    await esperarAResposta();

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(aviso).not.toHaveBeenCalled();
  });

  it("antesDaRespostaNaoMostraLink", () => {
    render(<LinkDaConta obter={() => new Promise<Conta | null>(() => {})} />);

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
