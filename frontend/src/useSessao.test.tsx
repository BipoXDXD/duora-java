import { act, render } from "@testing-library/react";
import { createRef, type RefObject } from "react";
import { describe, expect, it, vi } from "vitest";
import type { CenaRef } from "./componentes/Cena";
import type { EstadoDaSessao, PedidoDeFoco, Sessao } from "./sessao/sessao";
import { marcasDaCena, useSessao } from "./useSessao";

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

/** Uma sessão de mentira: o teste publica retratos e pedidos de foco por ela. */
function sessaoFalsa() {
  let retrato: EstadoDaSessao = { ...ENCERRADO, tela: "carregando" };
  let foco: PedidoDeFoco | null = null;
  const ouvintes = new Set<() => void>();
  const sessao = {
    obter: () => retrato,
    assinar: (ouvinte: () => void) => {
      ouvintes.add(ouvinte);
      return () => ouvintes.delete(ouvinte);
    },
    consumirFoco: () => {
      const pedido = foco;
      foco = null;
      return pedido;
    },
  } as unknown as Sessao;
  const publicar = (novo: EstadoDaSessao, pedido: PedidoDeFoco | null = null) => {
    retrato = novo;
    foco = pedido;
    act(() => {
      ouvintes.forEach((o) => {
        o();
      });
    });
  };
  return { sessao, publicar };
}

/** A página mínima: o título do encerrado e a mesma peça no inventário e na cena. */
function Pagina({ sessao, refCena }: { sessao: Sessao; refCena: RefObject<CenaRef | null> }) {
  const estado = useSessao(sessao, refCena);
  return (
    <>
      {estado.tela === "encerrado" && (
        <h2 id="encerrado-titulo" tabIndex={-1}>
          Você saiu do terraço
        </h2>
      )}
      <div id="pecas">
        <button type="button" data-peca="manta">
          Manta na cena
        </button>
      </div>
      <ul id="inventario">
        <li>
          <button type="button" data-peca="manta">
            Manta no inventário
          </button>
        </li>
      </ul>
    </>
  );
}

function montar(cena: CenaRef | null = null) {
  const { sessao, publicar } = sessaoFalsa();
  const refCena = createRef<CenaRef | null>();
  refCena.current = cena;
  const { container } = render(<Pagina sessao={sessao} refCena={refCena} />);
  return { container, publicar };
}

describe("useSessao", () => {
  it("o pedido do título do encerrado foca o título que acabou de aparecer", () => {
    const { container, publicar } = montar();

    publicar(ENCERRADO, { alvo: "titulo-encerrado" });

    expect(document.activeElement).toBe(container.querySelector("#encerrado-titulo"));
  });

  it("o pedido do item foca a peça no inventário, não o botão dela na cena", () => {
    const { container, publicar } = montar();

    publicar(ENCERRADO, { alvo: "item", peca: "manta" });

    expect(document.activeElement).toBe(container.querySelector("#inventario [data-peca=manta]"));
  });

  it("o pedido da peça na cena foca o botão dela na cena, não o item do inventário", () => {
    // Como a cena foca o botão na camada dela já está em cena.test.ts; aqui, só quem recebe o pedido.
    const cena = { focarPrimeiroLugar: vi.fn(() => false), focarPeca: vi.fn(() => true) };
    const { container, publicar } = montar(cena);

    publicar(ENCERRADO, { alvo: "peca-na-cena", peca: "manta" });

    expect(cena.focarPeca).toHaveBeenCalledExactlyOnceWith("manta");
    expect(document.activeElement).not.toBe(container.querySelector("#inventario [data-peca=manta]"));
  });

  it("depois de uma recusa sem lugar livre, o foco volta à peça no inventário", () => {
    const cena = { focarPrimeiroLugar: vi.fn(() => false), focarPeca: vi.fn(() => false) };
    const { container, publicar } = montar(cena);

    publicar(ENCERRADO, { alvo: "apos-recusa", peca: "manta" });

    expect(cena.focarPrimeiroLugar).toHaveBeenCalledOnce();
    expect(document.activeElement).toBe(container.querySelector("#inventario [data-peca=manta]"));
  });

  it("depois de uma recusa com lugar livre, o foco fica no lugar que a cena focou", () => {
    const cena = { focarPrimeiroLugar: vi.fn(() => true), focarPeca: vi.fn(() => false) };
    const { container, publicar } = montar(cena);
    const naCena = container.querySelector<HTMLElement>("#pecas [data-peca=manta]");
    naCena?.focus();

    publicar(ENCERRADO, { alvo: "apos-recusa", peca: "manta" });

    expect(document.activeElement).toBe(naCena);
  });
});

describe("marcasDaCena", () => {
  it("marcasDaCenaForaDaMontagemSaoVazias", () => {
    expect(marcasDaCena(ENCERRADO)).toEqual({ selecionada: null, sugerindo: null, destaques: [] });
  });
});
