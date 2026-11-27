import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Conta } from "../api/estadoDaConta";
import { ErroHttp } from "../api/http";
import { LinkDaConta } from "./LinkDaConta";

const ATIVA: Conta = { estado: "ATIVA", cadastroConcluido: true, verificada: false };

/** Deixa as promessas já resolvidas (e as atualizações de estado delas) acontecerem. */
async function esperarAResposta(): Promise<void> {
  await new Promise((resolver) => setTimeout(resolver, 0));
}

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
    render(<LinkDaConta obter={() => Promise.reject(new ErroHttp({ status: 500, codigo: "SERVIDOR" }))} />);

    await esperarAResposta();

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("antesDaRespostaNaoMostraLink", () => {
    render(<LinkDaConta obter={() => new Promise<Conta | null>(() => {})} />);

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
