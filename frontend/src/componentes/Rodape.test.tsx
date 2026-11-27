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
  vi.restoreAllMocks();
});

describe("Rodape", () => {
  it("temOsTresLinks", async () => {
    render(<Rodape obterContato={() => Promise.resolve("teste@exemplo.com")} />);

    expect(await screen.findByRole("link", { name: "Contato" })).toHaveAttribute("href", "mailto:teste@exemplo.com");
    expect(screen.getByRole("link", { name: "Termos de uso" })).toHaveAttribute("href", "/termos");
    expect(screen.getByRole("link", { name: "Privacidade" })).toHaveAttribute("href", "/privacidade");
  });

  it("temDenunciarEEncarregado", () => {
    render(<Rodape obterContato={() => new Promise<string>(() => {})} />);

    expect(screen.getByRole("link", { name: "Denunciar" })).toHaveAttribute("href", "/denunciar");
    expect(screen.getByRole("link", { name: "Encarregado de dados" })).toHaveAttribute("href", "/encarregado");
  });

  it("semContatoAindaTemTermosEPrivacidade", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    render(<Rodape obterContato={() => Promise.reject(new ErroHttp({ status: 0, codigo: "REDE" }))} />);

    await esperarAResposta();

    expect(screen.getByRole("link", { name: "Termos de uso" })).toHaveAttribute("href", "/termos");
    expect(screen.getByRole("link", { name: "Privacidade" })).toHaveAttribute("href", "/privacidade");
    expect(screen.queryByRole("link", { name: "Contato" })).not.toBeInTheDocument();
  });

  it("respostaInvalidaAvisaNoConsole", async () => {
    const aviso = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(() =>
        Promise.resolve(
          new Response(JSON.stringify({ contato: 42 }), { headers: { "Content-Type": "application/json" } }),
        ),
      ),
    );

    render(<Rodape />);

    await vi.waitFor(() => {
      expect(aviso).toHaveBeenCalledWith(
        "Contato do rodapé indisponível:",
        expect.objectContaining({ codigo: "RESPOSTA_INVALIDA" }),
      );
    });
    expect(screen.queryByRole("link", { name: "Contato" })).not.toBeInTheDocument();
  });

  it("linkInjetadoLevaOsDocumentosMasNaoOContato", async () => {
    render(
      <Rodape
        obterContato={() => Promise.resolve("teste@exemplo.com")}
        link={({ href, className, children }) => (
          <a href={`/falso${href}`} className={className}>
            {children}
          </a>
        )}
      />,
    );

    expect(await screen.findByRole("link", { name: "Contato" })).toHaveAttribute("href", "mailto:teste@exemplo.com");
    expect(screen.getByRole("link", { name: "Termos de uso" })).toHaveAttribute("href", "/falso/termos");
    expect(screen.getByRole("link", { name: "Privacidade" })).toHaveAttribute("href", "/falso/privacidade");
    expect(screen.getByRole("link", { name: "Denunciar" })).toHaveAttribute("href", "/falso/denunciar");
    expect(screen.getByRole("link", { name: "Encarregado de dados" })).toHaveAttribute("href", "/falso/encarregado");
  });

  it("antesDaRespostaNaoMostraContato", () => {
    render(<Rodape obterContato={() => new Promise<string>(() => {})} />);

    expect(screen.getAllByRole("link")).toHaveLength(4);
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
