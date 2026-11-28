import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Desfecho, DesfechoDaVerificacao } from "./Desfecho";

describe("Desfecho", () => {
  it("aprovadaLevaAoPerfil", () => {
    render(
      <DesfechoDaVerificacao
        tipo="aprovada"
        focar={false}
        tentativas={0}
        emailDoEncarregado="encarregado@exemplo.invalid"
        aoRepetir={() => undefined}
      />,
    );

    const links = screen.getAllByRole("link");
    expect(links[0]).toHaveAccessibleName("Montar meu perfil");
    expect(links[0]).toHaveAttribute("href", "/perfil");
    expect(screen.queryByRole("link", { name: "Continuar" })).not.toBeInTheDocument();
  });

  it("focoNoTituloDoDesfecho", () => {
    render(
      <Desfecho titulo="Idade confirmada">
        <p>Confirmamos que você tem 18 anos ou mais.</p>
      </Desfecho>,
    );

    expect(screen.getByRole("heading", { level: 2, name: "Idade confirmada" })).toHaveFocus();
    expect(screen.getByText("Confirmamos que você tem 18 anos ou mais.")).toBeInTheDocument();
  });

  it("semFocarDeixaOFocoOndeEstava", () => {
    render(<Desfecho titulo="Conta em análise" focar={false} />);

    expect(screen.getByRole("heading", { level: 2, name: "Conta em análise" })).not.toHaveFocus();
  });

  it("cadaSecaoTemONomeDoProprioTitulo", () => {
    render(
      <>
        <Desfecho titulo="Primeiro" focar={false} />
        <Desfecho titulo="Segundo" focar={false} />
      </>,
    );

    expect(screen.getByRole("region", { name: "Primeiro" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Segundo" })).toBeInTheDocument();
  });
});
