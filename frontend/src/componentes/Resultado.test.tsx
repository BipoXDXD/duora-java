import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import type { Visao } from "../dominio/visao";
import { renderComCenario } from "../teste/comCenario";
import { Resultado } from "./Resultado";

const REVELADO: Visao = {
  fase: "REVELADO",
  versao: 4,
  voce: { assento: "A", nome: "Ana" },
  parceiro: { assento: "B", nome: "Bruno", conectado: true },
  convite: null,
  pecas: [
    { peca: "telescopio", assento: "A", lugar: "C1" },
    { peca: "manta", assento: "A", lugar: "L1" },
    { peca: "banco", assento: "B", lugar: "C2" },
    { peca: "lanterna", assento: "B", lugar: "L2" },
  ],
  prontos: { voce: true, parceiro: true },
  confirmacao: { podeConfirmar: false, faltam: 0 },
  sinal: null,
  resultado: { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "esquerdo" },
  ajuste: null,
  abertura: null,
};

/** A tela cheia de verdade mora no App (useTelaCheia); aqui basta o liga-desliga. */
function ComTelaCheia() {
  const [telaCheia, setTelaCheia] = useState(true);
  return (
    <Resultado
      visao={REVELADO}
      enviando={null}
      acoes={{ pedirAjuste: () => Promise.resolve() }}
      telaCheia={telaCheia}
      aoVerPainel={() => {
        setTelaCheia(false);
      }}
      aoVerCeu={() => {
        setTelaCheia(true);
      }}
    />
  );
}

describe("Resultado", () => {
  it("ver o painel e ver o céu trocam de lugar levando o foco junto", async () => {
    const usuario = userEvent.setup();
    renderComCenario(<ComTelaCheia />);

    await usuario.click(screen.getByRole("button", { name: "Ver o painel" }));
    expect(screen.getByRole("button", { name: "Ver o céu" })).toHaveFocus();

    await usuario.click(screen.getByRole("button", { name: "Ver o céu" }));
    expect(screen.getByRole("button", { name: "Ver o painel" })).toHaveFocus();
  });

  it("sem clique na troca, a tela cheia mudar sozinha não puxa o foco", () => {
    const { rerender } = renderComCenario(
      <Resultado
        visao={REVELADO}
        enviando={null}
        acoes={{ pedirAjuste: () => Promise.resolve() }}
        telaCheia={true}
        aoVerPainel={() => {}}
        aoVerCeu={() => {}}
      />,
    );

    rerender(
      <Resultado
        visao={REVELADO}
        enviando={null}
        acoes={{ pedirAjuste: () => Promise.resolve() }}
        telaCheia={false}
        aoVerPainel={() => {}}
        aoVerCeu={() => {}}
      />,
    );

    expect(screen.getByRole("button", { name: "Ver o céu" })).not.toHaveFocus();
  });
});
