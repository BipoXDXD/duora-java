import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SeloVerificado } from "./SeloVerificado";

describe("SeloVerificado", () => {
  it("seloTemTextoAlemDoIcone", () => {
    const { container } = render(<SeloVerificado />);

    expect(screen.getByText("Verificado")).toBeInTheDocument();
    expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});
