import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";
import { Aguardando } from "./Aguardando";

describe("Aguardando", () => {
  it("mostraOProximoPasso", async () => {
    const roteador = createMemoryRouter([{ path: "/aguardando", element: <Aguardando /> }], {
      initialEntries: ["/aguardando"],
    });
    render(<RouterProvider router={roteador} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Cadastro recebido" })).toBeInTheDocument();
    expect(screen.getByText("Falta confirmar sua idade. Essa etapa chega em breve.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Minha conta" })).toHaveAttribute("href", "/conta");
    expect(document.title).toBe("Cadastro recebido · Duora");
  });
});
