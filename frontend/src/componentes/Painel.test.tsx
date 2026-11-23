import { render, screen } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it, vi } from "vitest";
import type { Visao, VisaoAbertura } from "../dominio/visao";
import type { AcoesDaSessao } from "../sessao/sessao";
import type { EstadoDaSessao } from "../sessao/tipos";
import { renderComCenario } from "../teste/comCenario";
import { Painel } from "./Painel";

const ENCERRADO: EstadoDaSessao = {
  tela: "encerrado",
  titulo: "Você saiu do terraço",
  texto: "O encontro terminou para as duas pessoas.",
  enviando: null,
  cenaVazia: true,
  confirmandoSaida: false,
  somLigado: false,
  momento: "crepusculo",
  ladoTelescopio: null,
};

function acoesFalsas(): AcoesDaSessao {
  return {
    escolherPeca: vi.fn(),
    moverPara: vi.fn(),
    retirar: vi.fn(),
    confirmar: vi.fn(),
    pedirAjuste: vi.fn(),
    recusarAjuste: vi.fn(),
    tocarAbertura: vi.fn(),
    criarOutro: vi.fn(),
    enviarApelido: vi.fn(),
    limparErroApelido: vi.fn(),
    continuarNoTerraco: vi.fn(),
  };
}

/** Ana na montagem do terraço, com a abertura dada. */
function montagem(abertura: VisaoAbertura | null): EstadoDaSessao {
  const visao: Visao = {
    fase: "MONTANDO",
    versao: 1,
    voce: { assento: "A", nome: "Ana" },
    parceiro: { assento: "B", nome: "Bruno", conectado: true },
    convite: null,
    pecas: [
      { peca: "telescopio", assento: "A", lugar: null, lugaresPermitidos: [], bloqueio: null },
      { peca: "manta", assento: "A", lugar: null, lugaresPermitidos: [], bloqueio: null },
      { peca: "banco", assento: "B", lugar: null },
    ],
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 4 },
    sinal: null,
    resultado: null,
    ajuste: null,
    abertura,
  };
  return {
    ...ENCERRADO,
    tela: "montagem",
    visao,
    selecionada: null,
    sugerindo: null,
    destaques: [],
    instrucao: "",
    cenaVazia: false,
  };
}

function desenharMontagem(abertura: VisaoAbertura | null) {
  return renderComCenario(
    <Painel
      estado={montagem(abertura)}
      refAviso={createRef()}
      acoes={acoesFalsas()}
      aoCopiarConvite={vi.fn()}
      aoAlternarSom={vi.fn()}
    />,
  );
}

describe("Painel", () => {
  it("entradaComConviteDizRecebeu", () => {
    renderComCenario(
      <Painel
        estado={{ ...ENCERRADO, tela: "entrada", comConvite: true, trocaDeSala: false, erroApelido: "" }}
        refAviso={createRef()}
        acoes={acoesFalsas()}
        aoCopiarConvite={vi.fn()}
        aoAlternarSom={vi.fn()}
      />,
    );

    expect(
      screen.getByText("Você recebeu um convite para montar um terraço a dois. Escolha seu apelido."),
    ).toBeInTheDocument();
  });

  it("painelEscondeInventarioDuranteAbertura", () => {
    const { container } = desenharMontagem({ ativa: true, voceTocou: false, outroTocou: false, acesa: false });

    expect(screen.getByRole("heading", { name: "Suas peças", hidden: true }).closest("section")).toHaveAttribute(
      "hidden",
    );
    expect(screen.getByRole("heading", { name: "O que falta", hidden: true }).closest("section")).toHaveAttribute(
      "hidden",
    );
    expect(container.querySelector(".acoes")).toHaveAttribute("hidden");
  });

  it("depoisDaAberturaOPainelMostraAsPecas", () => {
    desenharMontagem({ ativa: false, voceTocou: false, outroTocou: false, acesa: true });

    expect(screen.getByRole("heading", { name: "Suas peças" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Pronto para olhar o céu" })).toBeVisible();
  });

  it("fora da montagem o botão de som continua na página, oculto e com o estado do som", () => {
    const { container } = render(
      <Painel
        estado={ENCERRADO}
        refAviso={createRef()}
        acoes={acoesFalsas()}
        aoCopiarConvite={vi.fn()}
        aoAlternarSom={vi.fn()}
      />,
    );

    const som = container.querySelector("#som");
    expect(som).not.toBeVisible();
    expect(som).toHaveAttribute("aria-pressed", "false");
  });
});
