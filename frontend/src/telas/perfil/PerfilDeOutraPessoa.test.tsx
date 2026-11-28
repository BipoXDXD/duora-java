import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PerfilPublico } from "../../api/perfil";
import { stubarFetch } from "../../teste/fetchFalso";
import { PerfilDeOutraPessoa } from "./PerfilDeOutraPessoa";

const ANA: PerfilPublico = {
  apelido: "Ana",
  foto: "/api/fotos/0193f1c2-7a4b-7c3d-8e5f-0a1b2c3d4e5f/borrada",
  pronomes: "ela/dela",
  verificado: true,
};

afterEach(() => {
  vi.unstubAllGlobals();
});

/** Abre a tela com o que o loader da rota entregaria. */
function abrir(perfil: PerfilPublico | null) {
  const roteador = createMemoryRouter(
    [{ path: "/perfis/:contaId", loader: () => perfil, Component: PerfilDeOutraPessoa }],
    { initialEntries: ["/perfis/0193f1c2-7a4b-7c3d-8e5f-0a1b2c3d4e5f"] },
  );
  render(<RouterProvider router={roteador} />);
}

describe("PerfilDeOutraPessoa", () => {
  it("mostraABorrada", async () => {
    abrir(ANA);

    expect(await screen.findByRole("img", { name: "Foto de Ana" })).toHaveAttribute("src", ANA.foto);
    expect(screen.getByRole("heading", { level: 1, name: "Ana" })).toBeInTheDocument();
    expect(document.title).toBe("Ana · Duora");
    expect(screen.getByRole("link", { name: "Minha conta" })).toHaveAttribute("href", "/conta");
    expect(screen.getByRole("link", { name: "Voltar ao terraço" })).toHaveAttribute("href", "/");
  });

  it("semFotoMostraSilhueta", async () => {
    abrir({ ...ANA, foto: null });

    expect(await screen.findByRole("img", { name: "Sem foto" })).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: "Foto de Ana" })).toBeNull();
  });

  it("perfilInexistenteMostraIndisponivel", async () => {
    abrir(null);

    expect(await screen.findByRole("heading", { level: 1, name: "Perfil indisponível" })).toBeInTheDocument();
    expect(screen.getByText("Este perfil não existe ou não está disponível.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Minha conta" })).toHaveAttribute("href", "/conta");
    expect(screen.queryByRole("img")).toBeNull();
    expect(document.title).toBe("Perfil indisponível · Duora");
  });

  it("nuncaPedeANitida", async () => {
    const fetchFalso = stubarFetch();

    abrir(ANA);

    await screen.findByRole("img", { name: "Foto de Ana" });
    const enderecos = [...document.querySelectorAll("[src], [srcset], [href]")].flatMap((e) => [
      e.getAttribute("src") ?? "",
      e.getAttribute("srcset") ?? "",
      e.getAttribute("href") ?? "",
    ]);
    expect(enderecos.filter((endereco) => endereco.includes("nitida"))).toEqual([]);
    const pedidos = fetchFalso.mock.calls.map(([entrada]) => (typeof entrada === "string" ? entrada : ""));
    expect(pedidos.filter((url) => url.includes("nitida"))).toEqual([]);
  });

  it("seloVerificadoVisivel", async () => {
    abrir(ANA);

    expect(await screen.findByText("Verificado")).toBeVisible();
  });

  it("semSeloQuandoNaoVerificado", async () => {
    abrir({ ...ANA, verificado: false });

    await screen.findByRole("heading", { level: 1, name: "Ana" });
    expect(screen.queryByText("Verificado")).toBeNull();
  });
});
