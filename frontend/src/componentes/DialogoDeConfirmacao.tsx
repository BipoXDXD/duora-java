import { useEffect, useId, useRef, type ReactElement, type SyntheticEvent } from "react";
import { BOTAO_PERIGO, BOTAO_SECUNDARIO_SEM_MARGEM, seDisponivel } from "./estilos";

export interface PropsDoDialogoDeConfirmacao {
  aberto: boolean;
  titulo: string;
  texto: string;
  /** Rótulo do botão que confirma; repete o verbo do botão que abriu o diálogo. */
  acao: string;
  aoConfirmar: () => void;
  aoCancelar: () => void;
  /** Com a confirmação a caminho, nada no diálogo responde: a ação já saiu. */
  enviando: boolean;
}

const CANCELAR = "Cancelar";

/**
 * Confirmação de uma ação sem volta, no <dialog> nativo com showModal(): o navegador põe o diálogo no
 * topo, deixa o resto da página inerte (o foco fica preso nele) e trata o Esc como `cancel`. O estado
 * de aberto é de quem usa; o foco começa no Cancelar e, ao fechar, volta ao elemento que tinha o foco
 * quando o diálogo abriu (o botão de origem).
 */
export function DialogoDeConfirmacao({
  aberto,
  titulo,
  texto,
  acao,
  aoConfirmar,
  aoCancelar,
  enviando,
}: PropsDoDialogoDeConfirmacao): ReactElement {
  const dialogo = useRef<HTMLDialogElement>(null);
  const cancelar = useRef<HTMLButtonElement>(null);
  const origem = useRef<HTMLElement | null>(null);
  /** O `aberto` mais recente, lido no evento `close`. */
  const abertoAtual = useRef(aberto);
  const idDoTitulo = useId();
  const idDoTexto = useId();

  useEffect(() => {
    abertoAtual.current = aberto;
    const elemento = dialogo.current;
    if (elemento === null) {
      return;
    }
    if (aberto && !elemento.open) {
      origem.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      elemento.showModal();
      cancelar.current?.focus();
    } else if (!aberto && elemento.open) {
      elemento.close();
    }
  }, [aberto]);

  function aoPedirParaFechar(evento: SyntheticEvent<HTMLDialogElement>) {
    // O Esc vira o mesmo caminho do Cancelar: quem usa fecha pelo estado.
    evento.preventDefault();
    if (!enviando) {
      aoCancelar();
    }
  }

  function aoFechar() {
    // O navegador pode fechar sem perguntar (um segundo Esc seguido ignora o preventDefault): o estado
    // de quem usa acompanha.
    if (abertoAtual.current) {
      aoCancelar();
    }
    if (origem.current?.isConnected) {
      origem.current.focus();
    }
    origem.current = null;
  }

  return (
    <dialog
      ref={dialogo}
      aria-labelledby={idDoTitulo}
      aria-describedby={idDoTexto}
      onCancel={aoPedirParaFechar}
      onClose={aoFechar}
      className={[
        "m-auto w-[28rem] max-w-[calc(100vw-2*var(--e-4))] p-(--e-6) celular:p-(--e-5)",
        "rounded-(--raio) border border-ceu-600 bg-noite-800 text-marfim backdrop:bg-noite-950/80",
      ].join(" ")}
    >
      <h2 id={idDoTitulo} className="m-0 font-titulo font-semibold text-[1.5rem] leading-[1.2]">
        {titulo}
      </h2>
      <p id={idDoTexto} className="mt-(--e-3) mb-0 text-texto-2 leading-[1.5]">
        {texto}
      </p>
      <div className="mt-(--e-6) flex flex-wrap justify-end gap-(--e-3)">
        <button
          ref={cancelar}
          type="button"
          className={BOTAO_SECUNDARIO_SEM_MARGEM}
          aria-disabled={enviando ? true : undefined}
          onClick={seDisponivel(enviando, aoCancelar)}
        >
          {CANCELAR}
        </button>
        <button
          type="button"
          className={BOTAO_PERIGO}
          aria-disabled={enviando ? true : undefined}
          onClick={seDisponivel(enviando, aoConfirmar)}
        >
          {acao}
        </button>
      </div>
    </dialog>
  );
}
