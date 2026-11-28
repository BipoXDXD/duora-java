import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Desfecho } from "./Desfecho";

describe("Desfecho", () => {
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
});
