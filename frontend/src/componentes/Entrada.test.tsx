import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Entrada } from "./Entrada";

describe("Entrada", () => {
  it("apelido vazio mostra o erro e foca o campo, sem enviar", async () => {
    const aoEnviar = vi.fn();
    render(<Entrada comConvite={false} enviando={false} erro="" aoEnviar={aoEnviar} aoCorrigir={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: "Criar terraço" }));

    const campo = screen.getByLabelText("Seu apelido");
    expect(campo).toHaveAttribute("aria-invalid", "true");
    expect(campo).toHaveAccessibleDescription("De 1 a 20 caracteres. A outra pessoa vê este nome. Escreva um apelido.");
    expect(campo).toHaveFocus();
    expect(aoEnviar).not.toHaveBeenCalled();
  });

  it("com convite, o botão diz Entrar no terraço", () => {
    render(<Entrada comConvite enviando={false} erro="" aoEnviar={vi.fn()} aoCorrigir={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Entrar no terraço" })).toBeInTheDocument();
  });

  it("apelido válido vai sem espaços nas pontas", async () => {
    const aoEnviar = vi.fn();
    render(<Entrada comConvite={false} enviando={false} erro="" aoEnviar={aoEnviar} aoCorrigir={vi.fn()} />);

    await userEvent.type(screen.getByLabelText("Seu apelido"), "  Ana {Enter}");

    expect(aoEnviar).toHaveBeenCalledExactlyOnceWith("Ana");
  });

  it("o campo aceita 11 emojis simples (22 unidades UTF-16), que o servidor conta como 11", async () => {
    const aoEnviar = vi.fn();
    render(<Entrada comConvite={false} enviando={false} erro="" aoEnviar={aoEnviar} aoCorrigir={vi.fn()} />);
    const onze = "😀".repeat(11);

    await userEvent.type(screen.getByLabelText("Seu apelido"), `${onze}{Enter}`);

    expect(screen.getByLabelText("Seu apelido")).toHaveValue(onze);
    expect(aoEnviar).toHaveBeenCalledExactlyOnceWith(onze);
  });

  it("durante o envio, o botão diz Enviando… e não envia de novo", async () => {
    const aoEnviar = vi.fn();
    render(<Entrada comConvite={false} enviando erro="" aoEnviar={aoEnviar} aoCorrigir={vi.fn()} />);
    await userEvent.type(screen.getByLabelText("Seu apelido"), "Ana");

    const botao = screen.getByRole("button", { name: "Enviando…" });
    await userEvent.click(botao);

    expect(botao).toHaveAttribute("aria-disabled", "true");
    expect(aoEnviar).not.toHaveBeenCalled();
  });

  it("o erro do servidor aparece no campo e some quando o apelido volta a valer", async () => {
    const { rerender } = render(
      <Entrada comConvite={false} enviando={false} erro="" aoEnviar={vi.fn()} aoCorrigir={vi.fn()} />,
    );
    rerender(
      <Entrada
        comConvite={false}
        enviando={false}
        erro="Apelido indisponível."
        aoEnviar={vi.fn()}
        aoCorrigir={vi.fn()}
      />,
    );
    const campo = screen.getByLabelText("Seu apelido");
    expect(campo).toHaveAttribute("aria-invalid", "true");

    await userEvent.type(campo, "Bia");

    expect(campo).not.toHaveAttribute("aria-invalid");
    expect(screen.queryByText("Apelido indisponível.")).not.toBeInTheDocument();
  });

  it("avisa que o apelido foi corrigido quando o campo volta a valer depois de um erro", async () => {
    const aoCorrigir = vi.fn();
    render(
      <Entrada
        comConvite={false}
        enviando={false}
        erro="Apelido indisponível."
        aoEnviar={vi.fn()}
        aoCorrigir={aoCorrigir}
      />,
    );

    await userEvent.type(screen.getByLabelText("Seu apelido"), "Bia");

    expect(aoCorrigir).toHaveBeenCalled();
  });
});
