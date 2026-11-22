import type { KeyboardEvent } from "react";
import { textoConfirmarSaida } from "../dominio/mensagens";
import {
  BOTAO_PERIGO,
  BOTAO_SECUNDARIO_SEM_MARGEM,
  BOTAO_TEXTO,
  INDISPONIVEL_EM_ALTO_CONTRASTE,
  seDisponivel,
} from "./estilos";

interface PropsTopo {
  /** O Sair só existe no convite e na montagem. */
  mostrarSair: boolean;
  /** Nome do parceiro, ou null antes de ele entrar: muda o aviso e a ação da confirmação. */
  nomeParceiro: string | null;
  confirmando: boolean;
  enviandoSaida: boolean;
  aoAlternarSaida: () => void;
  aoContinuar: () => void;
  aoEncerrar: () => void;
}

const ID_CONFIRMACAO = "confirmar-saida";

/** No celular o topo tem altura fixa; com a confirmação aberta ele cresce e, em tela alta, deixa de ficar preso. */
const CLASSES_DO_TOPO = [
  "topo [grid-area:topo] flex flex-wrap items-baseline justify-between gap-(--e-4) px-(--e-6) pt-(--e-6) pb-0",
  "bg-noite-800 celular:items-center celular:h-(--altura-topo) celular:px-(--e-4) celular:py-0 celular:bg-noite-950",
  "celular:group-[.confirmando-saida]:h-auto celular:group-[.confirmando-saida]:min-h-(--altura-topo)",
  "celular:group-[.confirmando-saida]:pb-(--e-4)",
  // Deitado, o topo divide a coluna com o painel e pode quebrar em duas linhas (zoom de 200%).
  "celular-deitado:h-auto celular-deitado:min-h-(--altura-topo) celular-deitado:py-(--e-2)",
  "celular-alto:sticky celular-alto:top-0 celular-alto:z-5 celular-alto:group-[.confirmando-saida]:static",
].join(" ");

export function Topo({
  mostrarSair,
  nomeParceiro,
  confirmando,
  enviandoSaida,
  aoAlternarSaida,
  aoContinuar,
  aoEncerrar,
}: PropsTopo) {
  const { aviso, acao } = textoConfirmarSaida(nomeParceiro);

  function aoTeclar(evento: KeyboardEvent) {
    if (evento.key === "Escape" && confirmando && !enviandoSaida) {
      // Esc aqui só fecha a confirmação; a peça selecionada (se houver) continua escolhida.
      evento.stopPropagation();
      aoAlternarSaida();
    }
  }

  const indisponivel = enviandoSaida ? true : undefined;
  return (
    <header className={CLASSES_DO_TOPO}>
      <h1 className="titulo m-0 font-titulo font-semibold text-[1.75rem] leading-[1.1] tracking-[-0.01em] celular:text-[1.25rem]">
        Antes da primeira estrela
      </h1>
      <button
        type="button"
        className={`botao-sair ${BOTAO_TEXTO} flex-none aria-disabled:opacity-70 aria-disabled:cursor-progress ${INDISPONIVEL_EM_ALTO_CONTRASTE}`}
        id="sair"
        aria-expanded={confirmando}
        aria-controls={confirmando ? ID_CONFIRMACAO : undefined}
        aria-disabled={indisponivel}
        hidden={!mostrarSair}
        onClick={seDisponivel(enviandoSaida, aoAlternarSaida)}
        onKeyDown={aoTeclar}
      >
        Sair
      </button>
      <div
        className="confirmar-saida flex-[1_0_100%] grid gap-(--e-3) p-(--e-4) border border-ceu-600 rounded-(--raio) bg-noite-900"
        id={ID_CONFIRMACAO}
        role="group"
        aria-labelledby="confirmar-saida-texto"
        hidden={!confirmando}
        onKeyDown={aoTeclar}
      >
        <p className="m-0 text-texto-2 max-w-[40ch]" id="confirmar-saida-texto">
          {aviso}
        </p>
        <div className="confirmar-saida-acoes flex flex-wrap gap-(--e-2)">
          {/* Com a saída em envio, o bloco fica aberto até o servidor responder (como no Esc). */}
          <button
            type="button"
            className={BOTAO_SECUNDARIO_SEM_MARGEM}
            id="continuar"
            aria-disabled={indisponivel}
            onClick={seDisponivel(enviandoSaida, aoContinuar)}
          >
            Continuar no terraço
          </button>
          <button
            type="button"
            className={BOTAO_PERIGO}
            id="encerrar"
            aria-disabled={indisponivel}
            onClick={seDisponivel(enviandoSaida, aoEncerrar)}
          >
            {enviandoSaida ? "Enviando…" : acao}
          </button>
        </div>
      </div>
    </header>
  );
}
