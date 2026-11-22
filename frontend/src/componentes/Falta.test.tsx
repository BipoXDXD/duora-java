import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { CENARIO_MINIMO } from "../teste/cenarioMinimo";
import { renderComCenario } from "../teste/comCenario";
import { Falta } from "./Falta";

function visao(
  pecas: VisaoPeca[],
  parceiro: Visao["parceiro"] = { assento: "B", nome: "Bruno", conectado: true },
): Visao {
  return {
    fase: "MONTANDO",
    versao: 3,
    voce: { assento: "A", nome: "Ana" },
    parceiro,
    convite: null,
    pecas,
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 2 },
    sinal: null,
    resultado: null,
    ajuste: null,
  };
}

const minha = (peca: string, lugar: string | null): VisaoPeca => ({ peca, assento: "A", lugar });
const dele = (peca: string, lugar: string | null): VisaoPeca => ({ peca, assento: "B", lugar });

describe("Falta", () => {
  it("quatro linhas, cada uma com o estado para o leitor de tela", () => {
    renderComCenario(
      <Falta
        visao={visao([minha("telescopio", "C1"), minha("manta", null), dele("banco", null), dele("lanterna", "R1")])}
      />,
    );

    const linhas = within(screen.getByRole("region", { name: "O que falta" })).getAllByRole("listitem");
    expect(linhas.map((li) => li.textContent)).toEqual([
      "Telescópio no centro, pronto",
      "Banco no centro (Bruno), falta",
      "Sua segunda peça, falta",
      "Segunda peça de Bruno, pronto",
    ]);
    expect(linhas.map((li) => li.dataset.feito)).toEqual(["true", "false", "false", "true"]);
  });

  it("antes de o parceiro aparecer, fala da outra pessoa e da peça central", () => {
    renderComCenario(<Falta visao={visao([minha("telescopio", null), minha("manta", null)], null)} />);

    expect(screen.getAllByRole("listitem").map((li) => li.textContent)).toEqual([
      "Telescópio no centro, falta",
      "Peça central no centro (a outra pessoa), falta",
      "Sua segunda peça, falta",
      "Segunda peça de a outra pessoa, falta",
    ]);
  });

  it("some quando nada falta", () => {
    renderComCenario(
      <Falta
        visao={visao([minha("telescopio", "C1"), minha("manta", "L1"), dele("banco", "C2"), dele("lanterna", "R1")])}
      />,
    );

    expect(screen.queryByRole("region", { name: "O que falta" })).not.toBeInTheDocument();
    expect(screen.getByText("O que falta").closest("section")).toHaveAttribute("hidden");
  });

  it("checklist vem da zona do cenário", () => {
    renderComCenario(<Falta visao={visao([minha("pedra", "P1"), dele("folha", null)])} />, CENARIO_MINIMO);

    expect(screen.getAllByRole("listitem")[0]).toHaveTextContent("Pedra na cena");
  });
});
