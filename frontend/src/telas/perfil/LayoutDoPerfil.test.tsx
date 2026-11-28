import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { LayoutDoPerfil } from "./LayoutDoPerfil";

function abrirEm(caminho: string) {
  const roteador = createMemoryRouter(
    [
      {
        path: "/perfil",
        Component: LayoutDoPerfil,
        children: [
          { index: true, element: <h1>Meu perfil</h1> },
          { path: "namoro", element: <h1>Para namoro</h1> },
        ],
      },
      { path: "/", element: <h1>Terraço pelo roteador</h1> },
    ],
    { initialEntries: [caminho] },
  );
  render(<RouterProvider router={roteador} />);
  return roteador;
}

function navegacao() {
  return screen.getByRole("navigation", { name: "Seu perfil" });
}

/** Clique que nenhum script segurou (o navegador seguiria o href); o jsdom não navega de fato. */
function cliquesLivres(): { livres: string[]; desfazer: () => void } {
  const livres: string[] = [];
  const ouvir = (evento: MouseEvent) => {
    const alvo = evento.target instanceof Element ? evento.target.closest("a") : null;
    if (alvo !== null && !evento.defaultPrevented) {
      livres.push(alvo.getAttribute("href") ?? "");
    }
    evento.preventDefault();
  };
  window.addEventListener("click", ouvir);
  return {
    livres,
    desfazer: () => {
      window.removeEventListener("click", ouvir);
    },
  };
}

let desfazer: (() => void) | undefined;

afterEach(() => {
  desfazer?.();
  desfazer = undefined;
});

describe("LayoutDoPerfil", () => {
  it("marcaAPaginaAtual", () => {
    abrirEm("/perfil/namoro");

    expect(within(navegacao()).getByRole("link", { name: "Namoro" })).toHaveAttribute("aria-current", "page");
    for (const nome of ["Meu perfil", "Preferências", "Orientação"]) {
      expect(within(navegacao()).getByRole("link", { name: nome })).not.toHaveAttribute("aria-current");
    }
    expect(within(navegacao()).getByRole("link", { name: "Minha conta" })).toHaveAttribute("href", "/conta");
    expect(screen.getByRole("heading", { level: 1, name: "Para namoro" })).toBeInTheDocument();
  });

  it("voltarAoTerracoEPaginaInteira", async () => {
    const roteador = abrirEm("/perfil");
    const cliques = cliquesLivres();
    desfazer = cliques.desfazer;
    const voltar = within(navegacao()).getByRole("link", { name: "Voltar ao terraço" });

    await userEvent.click(voltar);

    expect(voltar).toHaveAttribute("href", "/");
    expect(cliques.livres).toEqual(["/"]);
    expect(roteador.state.location.pathname).toBe("/perfil");
  });
});
