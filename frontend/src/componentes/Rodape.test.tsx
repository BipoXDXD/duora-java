import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErroHttp } from "../api/http";
import { Rodape } from "./Rodape";

/** Deixa as promessas já resolvidas (e as atualizações de estado delas) acontecerem. */
async function esperarAResposta(): Promise<void> {
  await new Promise((resolver) => setTimeout(resolver, 0));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Rodape", () => {
  it("temOsTresLinks", async () => {
    render(<Rodape obterContato={() => Promise.resolve("teste@exemplo.com")} />);

    expect(await screen.findByRole("link", { name: "Contato" })).toHaveAttribute("href", "mailto:teste@exemplo.com");
    expect(screen.getByRole("link", { name: "Termos de uso" })).toHaveAttribute("href", "/termos");
    expect(screen.getByRole("link", { name: "Privacidade" })).toHaveAttribute("href", "/privacidade");
  });

  it("semContatoAindaTemTermosEPrivacidade", async () => {
    render(<Rodape obterContato={() => Promise.reject(new ErroHttp({ status: 0, codigo: "REDE" }))} />);

    await esperarAResposta();

    expect(screen.getByRole("link", { name: "Termos de uso" })).toHaveAttribute("href", "/termos");
    expect(screen.getByRole("link", { name: "Privacidade" })).toHaveAttribute("href", "/privacidade");
    expect(screen.queryByRole("link", { name: "Contato" })).not.toBeInTheDocument();
  });

  it("antesDaRespostaNaoMostraContato", () => {
    render(<Rodape obterContato={() => new Promise<string>(() => {})} />);

    expect(screen.getAllByRole("link")).toHaveLength(2);
  });

  it("semObterContatoLeOContatoDasVersoesLegais", async () => {
    const fetchFalso = vi.fn<typeof fetch>(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            termos: { versao: "2026-11-22" },
            privacidade: { versao: "2026-11-22" },
            contato: "teste@exemplo.com",
            encarregado: { nome: "o responsável pelo Duora", email: "encarregado@exemplo.invalid" },
          }),
          { headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
    vi.stubGlobal("fetch", fetchFalso);

    render(<Rodape />);

    expect(await screen.findByRole("link", { name: "Contato" })).toHaveAttribute("href", "mailto:teste@exemplo.com");
    expect(fetchFalso).toHaveBeenCalledWith("/api/legal/versoes", expect.objectContaining({ method: "GET" }));
  });
});
