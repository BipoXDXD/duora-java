import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FotoOuSilhueta } from "./FotoOuSilhueta";

const URL_DA_FOTO = "/api/fotos/abc/borrada";

describe("FotoOuSilhueta", () => {
  it("semUrlDesenhaSilhueta", () => {
    render(<FotoOuSilhueta url={null} alt="Foto de Lua" tamanho="grande" />);

    expect(screen.getByRole("img", { name: "Sem foto" })).toBeInTheDocument();
  });

  it("comUrlUsaOAlt", () => {
    render(<FotoOuSilhueta url={URL_DA_FOTO} alt="Foto de Lua" tamanho="medio" />);

    expect(screen.getByRole("img", { name: "Foto de Lua" })).toHaveAttribute("src", URL_DA_FOTO);
  });

  it("carregaSoQuandoChegaPerto", () => {
    render(<FotoOuSilhueta url={URL_DA_FOTO} alt="Foto de Lua" tamanho="medio" />);

    expect(screen.getByRole("img", { name: "Foto de Lua" })).toHaveAttribute("loading", "lazy");
  });

  it("erroAoCarregarViraSilhueta", () => {
    render(<FotoOuSilhueta url={URL_DA_FOTO} alt="Foto de Lua" tamanho="grande" />);

    fireEvent.error(screen.getByRole("img", { name: "Foto de Lua" }));

    expect(screen.getByRole("img", { name: "Sem foto" })).toBeInTheDocument();
  });

  it("urlNovaDepoisDoErroTentaDeNovo", () => {
    const { rerender } = render(<FotoOuSilhueta url={URL_DA_FOTO} alt="Foto de Lua" tamanho="grande" />);
    fireEvent.error(screen.getByRole("img", { name: "Foto de Lua" }));

    rerender(<FotoOuSilhueta url="/api/fotos/def/borrada" alt="Foto de Lua" tamanho="grande" />);

    expect(screen.getByRole("img", { name: "Foto de Lua" })).toHaveAttribute("src", "/api/fotos/def/borrada");
  });
});
