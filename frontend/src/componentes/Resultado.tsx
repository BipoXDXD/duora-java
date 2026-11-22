import { useLayoutEffect, useRef } from "react";
import { useCenario } from "../cenario/contexto";
import { textoResultado } from "../dominio/mensagens";
import type { Visao } from "../dominio/visao";
import type { AcoesDaSessao } from "../sessao/sessao";
import type { Acao } from "../sessao/tipos";
import { BOTAO_SECUNDARIO, TEXTO_SECUNDARIO, TITULO_DE_RESULTADO, seDisponivel } from "./estilos";

/** "Ver o painel" e "Ver o céu" só existem no celular em pé, onde a noite ocupa a tela. */
const BOTAO_DA_TELA_CHEIA = `${BOTAO_SECUNDARIO} ml-(--e-2) hidden celular-alto:inline-block`;

interface PropsResultado {
  visao: Visao;
  enviando: Acao | null;
  acoes: Pick<AcoesDaSessao, "ajustar">;
  /** A noite ocupa a tela do celular em pé: este bloco fica sobre ela, com "Ver o painel". */
  telaCheia: boolean;
  aoVerPainel: () => void;
  aoVerCeu: () => void;
}

/** A noite revelada. Fica no DOM, escondida, durante a montagem, como os outros blocos da montagem. */
export function Resultado({ visao, enviando, acoes, telaCheia, aoVerPainel, aoVerCeu }: PropsResultado) {
  const c = useCenario();
  const revelado = visao.fase === "REVELADO" && visao.resultado !== null;
  const { titulo, texto } = revelado ? textoResultado(c, visao) : { titulo: "", texto: "" };

  // "Ver o painel" e "Ver o céu" trocam de lugar: o foco passa de um para o outro, sem cair no body.
  const refTroca = useRef<HTMLButtonElement>(null);
  const trocou = useRef(false);
  useLayoutEffect(() => {
    if (trocou.current) {
      trocou.current = false;
      refTroca.current?.focus();
    }
  }, [telaCheia]);
  const trocar = (acao: () => void) => () => {
    trocou.current = true;
    acao();
  };

  return (
    <section className="resultado celular:order-3 celular-deitado:order-3" id="resultado" hidden={!revelado}>
      <h2 className={TITULO_DE_RESULTADO} id="resultado-titulo" tabIndex={-1}>
        {titulo}
      </h2>
      <p className={TEXTO_SECUNDARIO} id="resultado-texto">
        {texto}
      </p>
      <button
        type="button"
        className={BOTAO_SECUNDARIO}
        id="ajustar"
        aria-disabled={enviando !== null ? true : undefined}
        onClick={seDisponivel(enviando !== null, () => void acoes.ajustar())}
      >
        {enviando === "ajustar" ? "Enviando…" : "Ajustar de novo"}
      </button>
      {telaCheia ? (
        <button
          type="button"
          className={BOTAO_DA_TELA_CHEIA}
          id="ver-painel"
          ref={refTroca}
          onClick={trocar(aoVerPainel)}
        >
          Ver o painel
        </button>
      ) : (
        <button type="button" className={BOTAO_DA_TELA_CHEIA} id="ver-ceu" ref={refTroca} onClick={trocar(aoVerCeu)}>
          Ver o céu
        </button>
      )}
    </section>
  );
}
