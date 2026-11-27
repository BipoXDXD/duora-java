import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { semResposta, stubarFetch, VERSOES_LEGAIS } from "../../teste/fetchFalso";
import { Encarregado } from "./Encarregado";

function montar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={cliente}>
      <Encarregado />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Encarregado", () => {
  it("encarregadoMostraNomeEEmail", async () => {
    stubarFetch();

    montar();

    expect(await screen.findByText(VERSOES_LEGAIS.encarregado.nome)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: VERSOES_LEGAIS.encarregado.email })).toHaveAttribute(
      "href",
      `mailto:${VERSOES_LEGAIS.encarregado.email}`,
    );
    expect(screen.getByRole("heading", { level: 1, name: "Encarregado de dados" })).toBeInTheDocument();
    expect(
      screen.getByText("Use este contato para pedidos sobre seus dados pessoais (acesso, correção, exclusão)."),
    ).toBeInTheDocument();
    expect(document.title).toBe("Encarregado de dados · Duora");
  });

  it("antesDaRespostaAvisaQueCarrega", () => {
    vi.stubGlobal("fetch", semResposta);

    montar();

    expect(screen.getByRole("status")).toHaveTextContent("Carregando o contato…");
    expect(screen.getByRole("heading", { level: 1, name: "Encarregado de dados" })).toBeInTheDocument();
  });

  it("falhaDeixaTentarDeNovo", async () => {
    let pedidos = 0;
    const fetchDasVersoes = vi.fn<typeof fetch>(() => {
      pedidos += 1;
      return pedidos === 1
        ? Promise.reject(new TypeError("Failed to fetch"))
        : Promise.resolve(
            new Response(JSON.stringify(VERSOES_LEGAIS), { headers: { "Content-Type": "application/json" } }),
          );
    });
    vi.stubGlobal("fetch", fetchDasVersoes);

    montar();
    expect(
      await screen.findByText("Não conseguimos carregar o contato. Tente de novo em instantes."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Tentar de novo" }));

    expect(await screen.findByText(VERSOES_LEGAIS.encarregado.nome)).toBeInTheDocument();
  });
});
