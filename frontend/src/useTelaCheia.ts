// Tela cheia da revelação no celular em pé (R6): é de layout, não a Fullscreen API, porque quem
// recebe a noite pelo polling não fez gesto nenhum. "Ver o painel" sai dela até a próxima noite.

import { useEffect, useState, useSyncExternalStore } from "react";
import type { Momento } from "./dominio/dados";
import type { EstadoDaSessao } from "./sessao/sessao";

/** A mesma consulta da variante celular-alto (tokens.css, que aponta de volta para cá): em tela baixa a noite não cobre o painel. */
const CELULAR_ALTO = "(max-width: 860px) and (min-height: 700px)";

function consulta(): MediaQueryList | null {
  return typeof globalThis.matchMedia === "function" ? globalThis.matchMedia(CELULAR_ALTO) : null;
}

function assinar(avisar: () => void): () => void {
  const lista = consulta();
  lista?.addEventListener("change", avisar);
  return () => {
    lista?.removeEventListener("change", avisar);
  };
}

const ehCelularAlto = (): boolean => consulta()?.matches ?? false;

export interface TelaCheia {
  readonly telaCheia: boolean;
  readonly verPainel: () => void;
  readonly verCeu: () => void;
}

export function useTelaCheia(estado: EstadoDaSessao): TelaCheia {
  const celularAlto = useSyncExternalStore(assinar, ehCelularAlto, () => false);
  const [vendoPainel, setVendoPainel] = useState(false);
  const [momentoVisto, setMomentoVisto] = useState<Momento>(estado.momento);
  if (estado.momento !== momentoVisto) {
    // Cada passagem para a noite começa no céu (ajuste de estado no render, sem efeito extra).
    setMomentoVisto(estado.momento);
    setVendoPainel(false);
  }
  const telaCheia = celularAlto && estado.tela === "montagem" && estado.momento === "noite" && !vendoPainel;
  useEffect(() => {
    // O painel escondido ainda ocupa altura: sem travar, rolar a cena rola o documento por trás, a
    // barra do navegador encolhe, o dvh muda e a folha pula (iOS). Classe no html, sem :has().
    document.documentElement.classList.toggle("tela-cheia", telaCheia);
    return () => {
      document.documentElement.classList.remove("tela-cheia");
    };
  }, [telaCheia]);
  return {
    telaCheia,
    verPainel: () => {
      setVendoPainel(true);
    },
    verCeu: () => {
      setVendoPainel(false);
    },
  };
}
