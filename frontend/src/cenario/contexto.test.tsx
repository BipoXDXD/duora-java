import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderComCenario } from "../teste/comCenario";
import { useCenario } from "./contexto";

function NomeDoCenario() {
  return <p>{useCenario().nome}</p>;
}

describe("useCenario", () => {
  it("devolve o cenário do provedor", () => {
    renderComCenario(<NomeDoCenario />);

    expect(screen.getByText("Terraço das Estrelas Cadentes")).toBeInTheDocument();
  });

  it("fora do provedor lança erro", () => {
    // O React registra o erro de render no console antes de relançá-lo.
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    expect(() => render(<NomeDoCenario />)).toThrow("useCenario fora do ProvedorDeCenario");
    consoleError.mockRestore();
  });
});
