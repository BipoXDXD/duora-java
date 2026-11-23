import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
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
  conviteDaSala: "resumo-da-sala",
};

const SEM_ACAO = { pedirAjuste: () => Promise.resolve(), recusarAjuste: () => Promise.resolve() };

/** A tela cheia de verdade mora no App (useTelaCheia); aqui basta o liga-desliga. */
function ComTelaCheia() {
  const [telaCheia, setTelaCheia] = useState(true);
  return (
    <Resultado
      visao={REVELADO}
      enviando={null}
      acoes={SEM_ACAO}
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

function renderComAjuste(ajuste: Visao["ajuste"]) {
  const acoes = { pedirAjuste: vi.fn(() => Promise.resolve()), recusarAjuste: vi.fn(() => Promise.resolve()) };
  renderComCenario(
    <Resultado
      visao={{ ...REVELADO, ajuste }}
      enviando={null}
      acoes={acoes}
      telaCheia={false}
      aoVerPainel={() => {}}
      aoVerCeu={() => {}}
    />,
  );
  return acoes;
}

describe("Resultado: ajuste combinado", () => {
  it("semPedidoOferecePedirParaAjustar", async () => {
    const acoes = renderComAjuste(null);

    await userEvent.click(screen.getByRole("button", { name: "Pedir para ajustar" }));

    expect(acoes.pedirAjuste).toHaveBeenCalledOnce();
  });

  it("resultadoMostraAceitarERecusarParaQuemRecebe", async () => {
    const acoes = renderComAjuste({ deAssento: "B" });

    expect(screen.getByText("Bruno quer ajustar a composição.")).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Aceitar o ajuste" }));
    await userEvent.click(screen.getByRole("button", { name: "Agora não" }));

    expect(acoes.pedirAjuste).toHaveBeenCalledOnce();
    expect(acoes.recusarAjuste).toHaveBeenCalledOnce();
    expect(screen.queryByRole("button", { name: "Pedir para ajustar" })).toBeNull();
  });

  it("resultadoMostraCancelarParaQuemPediu", async () => {
    const acoes = renderComAjuste({ deAssento: "A" });

    expect(screen.getByText("Esperando Bruno aceitar.")).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Cancelar pedido" }));

    expect(acoes.recusarAjuste).toHaveBeenCalledOnce();
    expect(acoes.pedirAjuste).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "Aceitar o ajuste" })).toBeNull();
  });
});

describe("Resultado: botão do ajuste que muda sob o foco", () => {
  function comAjuste(ajuste: Visao["ajuste"], acoes: typeof SEM_ACAO) {
    return (
      <Resultado
        visao={{ ...REVELADO, ajuste }}
        enviando={null}
        acoes={acoes}
        telaCheia={false}
        aoVerPainel={() => {}}
        aoVerCeu={() => {}}
      />
    );
  }

  it.each([
    ["Aceitar o ajuste", { deAssento: "B" as const }],
    ["Cancelar pedido", { deAssento: "A" as const }],
  ])("%s que vira Pedir para ajustar leva o foco ao título e o Enter atrasado não pede", async (rotulo, ajuste) => {
    const usuario = userEvent.setup();
    const acoes = { pedirAjuste: vi.fn(() => Promise.resolve()), recusarAjuste: vi.fn(() => Promise.resolve()) };
    const { rerender } = renderComCenario(comAjuste(ajuste, acoes));
    screen.getByRole("button", { name: rotulo }).focus();

    rerender(comAjuste(null, acoes));
    await usuario.keyboard("{Enter}");

    expect(screen.getByRole("heading", { level: 2 })).toHaveFocus();
    expect(acoes.pedirAjuste).not.toHaveBeenCalled();
  });

  it("pedido que chega sem foco no botão não mexe no foco", () => {
    const { rerender } = renderComCenario(comAjuste({ deAssento: "B" }, SEM_ACAO));
    screen.getByRole("button", { name: "Ver o céu" }).focus();

    rerender(comAjuste(null, SEM_ACAO));

    expect(screen.getByRole("button", { name: "Ver o céu" })).toHaveFocus();
  });
});

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
        acoes={SEM_ACAO}
        telaCheia={true}
        aoVerPainel={() => {}}
        aoVerCeu={() => {}}
      />,
    );

    rerender(
      <Resultado
        visao={REVELADO}
        enviando={null}
        acoes={SEM_ACAO}
        telaCheia={false}
        aoVerPainel={() => {}}
        aoVerCeu={() => {}}
      />,
    );

    expect(screen.getByRole("button", { name: "Ver o céu" })).not.toHaveFocus();
  });
});
