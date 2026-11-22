// A cena continua imperativa (cena.ts, ceu.ts, meteoros.ts): os testes e2e dependem das mutações
// de DOM exatas que ela faz. O React só renderiza a moldura vazia e liga as camadas por ref.

import { useEffectEvent, useImperativeHandle, useLayoutEffect, useRef, type Ref, type RefObject } from "react";
import { criarCena, mostrarTerraco, type CenaImperativa } from "../cena/cena";
import { desenharCeu } from "../cena/ceu";
import { criarMeteoros, type Meteoros } from "../cena/meteoros";
import { VARIANTES, type Geometria, type Momento } from "../dominio/dados";
import type { Visao } from "../dominio/visao";

export interface CenaRef {
  /** Foca o primeiro lugar livre; diz se havia algum. */
  focarPrimeiroLugar(): boolean;
}

export interface PropsCena {
  visao: Visao | null;
  selecionada: string | null;
  enviando: boolean;
  momento: Momento;
  ladoTelescopio: string | null;
  /** Nula até o cena.json chegar; sem ela o terraço fica vazio. */
  geometria: Geometria | null;
  aoEscolherPeca: (peca: string, minha: boolean) => void;
  aoEscolherLugar: (lugar: string, bloqueio: string | null) => void;
  aoMeteoro: () => void;
  ref?: Ref<CenaRef>;
}

/** Refs de elementos renderizados por este componente existem em todo efeito de layout. */
function elemento<T>(ref: RefObject<T | null>): T {
  if (ref.current === null) {
    throw new Error("Elemento da cena ausente no efeito");
  }
  return ref.current;
}

/**
 * O redesenho troca os botões das camadas; devolve o foco ao equivalente (mesma peça ou mesmo
 * lugar na mesma camada), se ele tiver caído no body.
 */
function lembrarFoco(camadas: readonly HTMLElement[]): () => void {
  const ativo = document.activeElement;
  const camada = camadas.find((c) => ativo instanceof HTMLElement && ativo.isConnected && c.contains(ativo));
  if (!(ativo instanceof HTMLElement) || camada === undefined) {
    return () => {};
  }
  const { peca, lugar } = ativo.dataset;
  return () => {
    if (document.activeElement && document.activeElement !== document.body) {
      return;
    }
    const seletor = lugar ? `[data-lugar="${lugar}"]` : `[data-peca="${peca ?? ""}"]`;
    camada.querySelector<HTMLElement>(seletor)?.focus();
  };
}

export function Cena({
  visao,
  selecionada,
  enviando,
  momento,
  ladoTelescopio,
  geometria,
  aoEscolherPeca,
  aoEscolherLugar,
  aoMeteoro,
  ref,
}: PropsCena) {
  const refEstrelas = useRef<SVGSVGElement>(null);
  const refMeteoros = useRef<HTMLDivElement>(null);
  const refPalco = useRef<HTMLDivElement>(null);
  const refJanelas = useRef<HTMLDivElement>(null);
  const refPecas = useRef<HTMLDivElement>(null);
  const refLugares = useRef<HTMLDivElement>(null);
  const cena = useRef<CenaImperativa | null>(null);
  const meteoros = useRef<Meteoros | null>(null);

  const escolherPeca = useEffectEvent(aoEscolherPeca);
  const escolherLugar = useEffectEvent(aoEscolherLugar);
  const avisarMeteoro = useEffectEvent(aoMeteoro);

  useImperativeHandle(ref, () => ({
    focarPrimeiroLugar: () => cena.current?.focarPrimeiroLugar() ?? false,
  }));

  // Céu, terraço e meteoros não dependem da sessão: nascem com a cena.
  useLayoutEffect(() => {
    const estrelas = elemento(refEstrelas);
    const janelas = elemento(refJanelas);
    desenharCeu(estrelas, janelas);
    // O <img> do terraço nasce sem src, para não baixar duas larguras: a família sai do palco já
    // medido, sem esperar o cena.json.
    mostrarTerraco(elemento(refPalco), VARIANTES);
    const movimentoReduzido = window.matchMedia("(prefers-reduced-motion: reduce)");
    const instancia = criarMeteoros(elemento(refMeteoros), {
      aoMeteoro: () => {
        avisarMeteoro();
      },
      movimentoReduzido: () => movimentoReduzido.matches,
    });
    const reiniciar = () => {
      instancia.reiniciar();
    };
    movimentoReduzido.addEventListener("change", reiniciar);
    meteoros.current = instancia;
    return () => {
      movimentoReduzido.removeEventListener("change", reiniciar);
      instancia.limpar();
      meteoros.current = null;
      estrelas.replaceChildren();
      janelas.replaceChildren();
    };
  }, []);

  useLayoutEffect(() => {
    if (geometria === null) {
      return;
    }
    cena.current = criarCena({
      palco: elemento(refPalco),
      camadaPecas: elemento(refPecas),
      camadaLugares: elemento(refLugares),
      geometria,
      aoEscolherPeca: (peca, minha) => {
        escolherPeca(peca, minha);
      },
      aoEscolherLugar: (lugar, bloqueio) => {
        escolherLugar(lugar, bloqueio);
      },
    });
    return () => {
      cena.current = null;
    };
  }, [geometria]);

  useLayoutEffect(() => {
    const atual = cena.current;
    if (atual === null) {
      return;
    }
    const devolverFoco = lembrarFoco([elemento(refPecas), elemento(refLugares)]);
    if (visao === null || visao.fase === "AGUARDANDO_PAR") {
      atual.limpar();
    } else {
      atual.desenhar(visao, selecionada);
    }
    devolverFoco();
  }, [geometria, visao, selecionada]);

  useLayoutEffect(() => {
    elemento(refPalco).dataset.enviando = String(enviando);
  }, [enviando]);

  // Revelado: meteoros do lado do telescópio. Qualquer outro momento: céu sem meteoros.
  useLayoutEffect(() => {
    if (momento === "noite") {
      meteoros.current?.mostrar(ladoTelescopio);
    } else {
      meteoros.current?.limpar();
    }
  }, [momento, ladoTelescopio]);

  return (
    <section className="cena" aria-labelledby="cena-titulo">
      <h2 id="cena-titulo" className="so-leitor">
        Terraço
      </h2>
      <div className="ceu" aria-hidden="true">
        <svg
          className="estrelas"
          id="estrelas"
          ref={refEstrelas}
          viewBox="0 0 1000 600"
          preserveAspectRatio="xMidYMid slice"
        ></svg>
      </div>
      <div className="meteoros" id="meteoros" ref={refMeteoros} aria-hidden="true"></div>
      <div className="palco" id="palco" ref={refPalco}>
        <div className="cidade cidade-crepusculo" aria-hidden="true"></div>
        <div className="cidade cidade-noite" aria-hidden="true"></div>
        <svg className="horizonte" aria-hidden="true" viewBox="0 0 1000 120" preserveAspectRatio="none">
          <path d="M0 120V78h40v-14h28v22h36V52h22v-8h18v8h20v34h44V70h30V40h14v-10h10v10h16v46h52V66h26v-18h40v38h34V58h18v-6h22v6h18v28h60V72h38V50h24v36h46V62h28v-20h12v20h22v24h38V70h44v16h36V56h30v30h46V74h40v46z" />
        </svg>
        <div className="janelas" id="janelas" ref={refJanelas} aria-hidden="true"></div>
        <img className="terraco terraco-crepusculo" alt="" />
        <img className="terraco terraco-noite" alt="" loading="lazy" />
        <div className="camada-pecas" id="pecas" ref={refPecas}></div>
        <div className="camada-lugares" id="lugares" ref={refLugares}></div>
      </div>
    </section>
  );
}
