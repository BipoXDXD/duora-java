import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";
import { ErroHttp } from "../api/http";
import { ErroDaConta } from "./ErroDaConta";

function renderizarComLoader(loader: () => unknown) {
  const roteador = createMemoryRouter(
    [{ path: "/cadastro", loader, element: <h1>Tela aberta</h1>, ErrorBoundary: ErroDaConta }],
    { initialEntries: ["/cadastro"] },
  );
  render(<RouterProvider router={roteador} />);
}

describe("ErroDaConta", () => {
  it("contaIndisponivelMostraAMensagemDoServidor", async () => {
    renderizarComLoader(() => {
      throw new ErroHttp({ status: 403, codigo: "CONTA_INDISPONIVEL", mensagem: "Esta conta não está disponível." });
    });

    expect(await screen.findByRole("heading", { level: 1, name: "Conta indisponível" })).toBeInTheDocument();
    expect(screen.getByText("Esta conta não está disponível.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar ao terraço" })).toHaveAttribute("href", "/");
  });

  it("falhaQualquerDizQueAPaginaNaoAbriu", async () => {
    renderizarComLoader(() => {
      throw new TypeError("chunk não baixou");
    });

    expect(
      await screen.findByRole("heading", { level: 1, name: "Não conseguimos abrir esta página" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Pode ter sido a conexão ou uma falha nossa. Tente de novo em instantes."),
    ).toBeInTheDocument();
  });

  it("falhaDoServidorTentaOLoaderDeNovo", async () => {
    let leituras = 0;
    renderizarComLoader(() => {
      leituras += 1;
      if (leituras === 1) {
        throw new ErroHttp({ status: 503, codigo: "SERVIDOR" });
      }
      return null;
    });

    await userEvent.click(await screen.findByRole("button", { name: "Tentar de novo" }));

    expect(await screen.findByRole("heading", { name: "Tela aberta" })).toBeInTheDocument();
  });
});
