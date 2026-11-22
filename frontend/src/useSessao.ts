// A ponte entre a Sessao (sem React) e a tela: lê o retrato com useSyncExternalStore e, depois de
// cada commit, faz com o foco o que o desenhar() do main.js antigo fazia, na mesma ordem.

import { type RefObject, useCallback, useLayoutEffect, useRef, useSyncExternalStore } from "react";
import type { MarcasDaCena } from "./cena/cena";
import type { CenaRef } from "./componentes/Cena";
import type { Fase, Visao } from "./dominio/visao";
import { levarFocoSeSumiu } from "./sessao/foco";
import { naoAlcancavel } from "./sessao/naoAlcancavel";
import type { EstadoDaSessao, PedidoDeFoco, Sessao } from "./sessao/sessao";

/** A visão da sala, nas telas que têm uma. */
export function visaoDaTela(estado: EstadoDaSessao): Visao | null {
  return estado.tela === "convite" || estado.tela === "montagem" ? estado.visao : null;
}

/** A visão que a cena desenha: nenhuma quando a sessão terminou, mesmo com a tela parada. */
export function visaoDaCena(estado: EstadoDaSessao): Visao | null {
  return estado.cenaVazia ? null : visaoDaTela(estado);
}

const SEM_MARCAS: MarcasDaCena = { selecionada: null, sugerindo: null, destaques: [] };

/** O que a sessão marca na cena: só a montagem tem seleção, sugestão e destaques. */
export function marcasDaCena(estado: EstadoDaSessao): MarcasDaCena {
  if (estado.tela !== "montagem") {
    return SEM_MARCAS;
  }
  const { selecionada, sugerindo, destaques } = estado;
  return { selecionada, sugerindo, destaques };
}

function focarPorSeletor(seletor: string): boolean {
  const alvo = document.querySelector<HTMLElement>(seletor);
  alvo?.focus();
  return alvo !== null;
}

/** A peça no inventário, não o botão dela na cena (os dois têm data-peca). */
function focarItem(peca: string): void {
  focarPorSeletor(`#inventario [data-peca="${peca}"]`);
}

/** Cada alvo do PedidoDeFoco no elemento da página que o main.js antigo focava. */
function aplicarFoco(pedido: PedidoDeFoco, cena: CenaRef | null): void {
  switch (pedido.alvo) {
    case "apelido":
      focarPorSeletor("#apelido");
      return;
    case "link-convite":
      focarPorSeletor("#link-convite");
      return;
    case "titulo-encerrado":
      focarPorSeletor("#encerrado-titulo");
      return;
    case "sair":
      focarPorSeletor("#sair");
      return;
    case "continuar":
      focarPorSeletor("#continuar");
      return;
    case "primeiro-lugar":
      cena?.focarPrimeiroLugar();
      return;
    case "item":
      focarItem(pedido.peca);
      return;
    case "apos-recusa":
      if (!cena?.focarPrimeiroLugar()) {
        focarItem(pedido.peca);
      }
      return;
    default:
      naoAlcancavel(pedido);
  }
}

/**
 * O retrato da sessão para a tela. Depois de cada mudança dela, já com o DOM novo:
 *  1. se o elemento focado antes do render sumiu (ou o foco estava no body e a fase mudou), o foco
 *     vai ao destino da fase (a Cena, que é filha, já devolveu o foco dos botões que ela redesenha);
 *  2. o foco pedido pela sessão, se houver, vence.
 */
export function useSessao(sessao: Sessao, refCena: RefObject<CenaRef | null>): EstadoDaSessao {
  // O elemento focado antes do render que a mudança vai causar; undefined: nada mudou na sessão.
  const ativoAntes = useRef<Element | null | undefined>(undefined);
  // A fase do último desenho com sala: trocar de tela com o foco no body também leva o foco ao destino.
  const faseDesenhada = useRef<Fase | null>(null);

  const assinar = useCallback(
    (avisarReact: () => void) =>
      sessao.assinar(() => {
        if (ativoAntes.current === undefined) {
          ativoAntes.current = document.activeElement;
        }
        avisarReact();
      }),
    [sessao],
  );
  const estado = useSyncExternalStore(assinar, sessao.obter);

  useLayoutEffect(() => {
    const ativo = ativoAntes.current;
    if (ativo === undefined) {
      return;
    }
    ativoAntes.current = undefined;
    const visao = visaoDaTela(estado);
    if (visao === null) {
      faseDesenhada.current = null;
    } else {
      // A primeira tela da sessão (fase nula) também conta como troca: no Safari o clique não foca
      // o botão, então o foco cai no body quando a tela de entrada some.
      levarFocoSeSumiu(ativo, faseDesenhada.current !== visao.fase, visao.fase, document);
      faseDesenhada.current = visao.fase;
    }
    const pedido = sessao.consumirFoco();
    if (pedido !== null) {
      aplicarFoco(pedido, refCena.current);
    }
  }, [estado, sessao, refCena]);

  return estado;
}
