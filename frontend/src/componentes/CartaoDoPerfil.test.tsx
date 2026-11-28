import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { PerfilPublico } from "../api/perfil";
import { CartaoDoPerfil } from "./CartaoDoPerfil";

const LUA: PerfilPublico = { apelido: "Lua", foto: "/api/fotos/abc/borrada", pronomes: "elu/delu", verificado: true };

describe("CartaoDoPerfil", () => {
  it("mostraApelidoEAFotoSemEsperarARolagem", () => {
    render(<CartaoDoPerfil perfil={LUA} />);

    expect(screen.getByText("Lua")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Foto de Lua" })).toHaveAttribute("loading", "eager");
  });

  it("carregamentoRepassadoAFoto", () => {
    render(<CartaoDoPerfil perfil={LUA} carregamento="lazy" />);

    expect(screen.getByRole("img", { name: "Foto de Lua" })).toHaveAttribute("loading", "lazy");
  });

  it("semApelidoNaoRepeteOTitulo", () => {
    render(<CartaoDoPerfil perfil={LUA} semApelido />);

    expect(screen.queryByText("Lua")).toBeNull();
    expect(screen.getByRole("img", { name: "Foto de Lua" })).toBeInTheDocument();
  });

  it("semFotoMostraSilhueta", () => {
    render(<CartaoDoPerfil perfil={{ ...LUA, foto: null }} />);

    expect(screen.getByRole("img", { name: "Sem foto" })).toBeInTheDocument();
  });

  it("mostraPronomesQuandoHa", () => {
    render(<CartaoDoPerfil perfil={LUA} />);

    expect(screen.getByText("elu/delu")).toBeInTheDocument();
  });

  it("semPronomesNaoDeixaEspacoVazio", () => {
    const { container } = render(<CartaoDoPerfil perfil={{ ...LUA, pronomes: null, verificado: false }} />);

    const vazios = [...container.querySelectorAll("p, span, div")].filter((e) => e.textContent.trim() === "");
    expect(vazios.filter((e) => e.querySelector("img, svg") === null)).toEqual([]);
  });

  it("seloSoComVerificado", () => {
    const { rerender } = render(<CartaoDoPerfil perfil={LUA} />);
    expect(screen.getByText("Verificado")).toBeInTheDocument();

    rerender(<CartaoDoPerfil perfil={{ ...LUA, verificado: false }} />);

    expect(screen.queryByText("Verificado")).toBeNull();
  });
});
