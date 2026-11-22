import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { Inventario } from "./Inventario";

function peca(peca: string, lugar: string | null, bloqueio: string | null = null): VisaoPeca {
  return { peca, assento: "A", lugar, lugaresPermitidos: [], bloqueio };
}

function visao(pecas: VisaoPeca[]): Visao {
  return {
    fase: "MONTANDO",
    versao: 3,
    voce: { assento: "A", nome: "Ana" },
    parceiro: { assento: "B", nome: "Bruno", conectado: true },
    convite: null,
    pecas: [...pecas, { peca: "banco", assento: "B", lugar: null }],
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 2 },
    sinal: null,
    resultado: null,
  };
}

const MONTANDO = visao([peca("telescopio", "C1"), peca("manta", "L1"), peca("radio", null)]);

function renderizar(v: Visao, mudancas: Partial<Parameters<typeof Inventario>[0]> = {}) {
  const props = { visao: v, selecionada: null, enviando: null, aoEscolher: vi.fn(), aoRetirar: vi.fn(), ...mudancas };
  render(<Inventario {...props} />);
  return props;
}

const item = (nome: RegExp) => screen.getByRole("button", { name: nome });

describe("Inventario", () => {
  it("a segunda peça lateral mostra a troca", () => {
    renderizar(MONTANDO);

    expect(within(item(/^Rádio/)).getByText("Troca a manta por o rádio")).toBeInTheDocument();
  });

  it("item bloqueado tem aria-disabled, diz o motivo e ignora o clique", async () => {
    const props = renderizar(visao([peca("telescopio", null, "ESPERE_PRIMEIRA_PECA"), peca("manta", null)]));

    const telescopio = item(/^Telescópio/);
    await userEvent.click(telescopio);

    expect(telescopio).toHaveAttribute("aria-disabled", "true");
    expect(telescopio).toHaveTextContent("Espere Bruno colocar a primeira peça.");
    expect(props.aoEscolher).not.toHaveBeenCalled();
  });

  it("cada peça diz onde está ou para onde vai, com o aviso antes das laterais", () => {
    renderizar(visao([peca("telescopio", "C1"), peca("manta", null), peca("radio", null)]));

    const lista = screen.getByRole("region", { name: "Suas peças" });
    expect(
      within(lista)
        .getAllByRole("listitem")
        .map((li) => li.textContent),
    ).toEqual([
      "TelescópioNo terraço: centro, à esquerda",
      "Escolha uma destas para as laterais:",
      "MantaVai em um dos lugares laterais",
      "RádioVai em um dos lugares laterais",
    ]);
  });

  it("clicar num item livre escolhe a peça e marca aria-pressed só na selecionada", async () => {
    const props = renderizar(MONTANDO, { selecionada: "manta" });

    await userEvent.click(item(/^Rádio/));

    expect(props.aoEscolher).toHaveBeenCalledExactlyOnceWith("radio");
    expect(item(/^Manta/)).toHaveAttribute("aria-pressed", "true");
    expect(item(/^Rádio/)).toHaveAttribute("aria-pressed", "false");
  });

  it("a miniatura usa o sprite do crepúsculo na família menor", () => {
    renderizar(MONTANDO);

    const miniatura = item(/^Telescópio/).querySelector("img.miniatura");
    expect(miniatura).toHaveAttribute("src", "/assets/cena/telescopio-esq-crepusculo-900.webp");
    expect(miniatura).toHaveAttribute("alt", "");
  });

  it("Tirar do terraço só aparece com uma lateral colocada e selecionada", () => {
    renderizar(MONTANDO, { selecionada: "manta" });

    expect(screen.getByRole("button", { name: "Tirar do terraço" })).toBeVisible();
  });

  it("a peça central selecionada não pode ser tirada", () => {
    renderizar(MONTANDO, { selecionada: "telescopio" });

    expect(screen.queryByRole("button", { name: "Tirar do terraço" })).not.toBeInTheDocument();
  });

  it("durante o envio da retirada, o botão diz Enviando… e os itens ficam indisponíveis", async () => {
    const props = renderizar(MONTANDO, { selecionada: "manta", enviando: "retirar" });

    await userEvent.click(screen.getByRole("button", { name: "Enviando…" }));
    await userEvent.click(item(/^Rádio/));

    expect(props.aoRetirar).not.toHaveBeenCalled();
    expect(props.aoEscolher).not.toHaveBeenCalled();
    expect(item(/^Rádio/)).toHaveAttribute("aria-disabled", "true");
  });
});
