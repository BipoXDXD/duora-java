import { useLayoutEffect, useRef } from "react";
import { useCenario } from "../cenario/contexto";
import { type SituacaoDoAjuste, situacaoDoAjuste, textoDoPedido } from "../dominio/ajuste";
import { textoResultado } from "../dominio/mensagens";
import type { Visao } from "../dominio/visao";
import type { AcoesDaSessao } from "../sessao/sessao";
import type { Acao } from "../sessao/tipos";
import { BOTAO_SECUNDARIO, TEXTO_SECUNDARIO, TITULO_DE_RESULTADO, seDisponivel } from "./estilos";

/** "Ver o painel" e "Ver o céu" só existem no celular em pé, onde a noite ocupa a tela. */
const AO_LADO = `${BOTAO_SECUNDARIO} ml-(--e-2)`;
const BOTAO_DA_TELA_CHEIA = `${AO_LADO} hidden celular-alto:inline-block`;

/** O botão do ajuste em cada situação: id e rótulo. */
const BOTAO_DO_AJUSTE: Record<SituacaoDoAjuste, [string, string]> = {
  nenhum: ["ajustar", "Pedir para ajustar"],
  meu: ["cancelar-ajuste", "Cancelar pedido"],
  "do-outro": ["aceitar-ajuste", "Aceitar o ajuste"],
};

interface PropsResultado {
  visao: Visao;
  enviando: Acao | null;
  acoes: Pick<AcoesDaSessao, "pedirAjuste" | "recusarAjuste">;
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
  const situacao = situacaoDoAjuste(visao);
  const meu = situacao === "meu";
  const [idDoAjuste, rotuloDoAjuste] = BOTAO_DO_AJUSTE[situacao];
  const pedido = textoDoPedido(visao);

  // O botão do ajuste vira "Pedir para ajustar" debaixo do foco (o outro cancelou ou recusou, ou o
  // próprio pedido foi cancelado): um Enter atrasado mandaria um pedido novo. O foco vai ao título,
  // e o aviso conta o que mudou.
  const refTitulo = useRef<HTMLHeadingElement>(null);
  const refAjuste = useRef<HTMLButtonElement>(null);
  const situacaoAntes = useRef(situacao);
  useLayoutEffect(() => {
    const antes = situacaoAntes.current;
    situacaoAntes.current = situacao;
    const focado = refAjuste.current !== null && refAjuste.current.ownerDocument.activeElement === refAjuste.current;
    if (revelado && antes !== "nenhum" && situacao === "nenhum" && focado) {
      refTitulo.current?.focus();
    }
  }, [situacao, revelado]);

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
      <h2 className={TITULO_DE_RESULTADO} id="resultado-titulo" tabIndex={-1} ref={refTitulo}>
        {titulo}
      </h2>
      <p className={TEXTO_SECUNDARIO} id="resultado-texto">
        {texto}
      </p>
      {pedido && (
        <p className="mx-0 mb-0 mt-(--e-3) text-marfim" id="ajuste-pedido">
          {pedido}
        </p>
      )}
      {/* Um botão só em todas as situações: o foco do teclado fica nele quando o pedido muda. */}
      <button
        type="button"
        className={BOTAO_SECUNDARIO}
        id={idDoAjuste}
        ref={refAjuste}
        aria-disabled={enviando !== null ? true : undefined}
        onClick={seDisponivel(enviando !== null, () => void (meu ? acoes.recusarAjuste() : acoes.pedirAjuste()))}
      >
        {enviando === (meu ? "recusar-ajuste" : "ajustar") ? "Enviando…" : rotuloDoAjuste}
      </button>
      {situacao === "do-outro" && (
        <button
          type="button"
          className={AO_LADO}
          id="recusar-ajuste"
          aria-disabled={enviando !== null ? true : undefined}
          onClick={seDisponivel(enviando !== null, () => void acoes.recusarAjuste())}
        >
          {enviando === "recusar-ajuste" ? "Enviando…" : "Agora não"}
        </button>
      )}
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
