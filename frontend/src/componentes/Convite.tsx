import { useRef } from "react";
import { BOTAO_SECUNDARIO_SEM_MARGEM, CAMPO, DICA, ENTRADA_DE_TEXTO, ROTULO, SUBTITULO, TELA } from "./estilos";

interface PropsConvite {
  link: string;
  /** Chamado depois de cada tentativa de cópia, com o aviso a anunciar no #aviso. */
  aoCopiar: (aviso: string) => void;
}

/**
 * Copia pela Clipboard API; sem ela (ou sem permissão), seleciona o campo e tenta o execCommand,
 * que é o único caminho em navegadores antigos e em páginas sem contexto seguro.
 */
async function copiar(link: string, campo: HTMLInputElement | null): Promise<string> {
  try {
    await navigator.clipboard.writeText(link);
    return "Convite copiado. Abra-o em outra janela anônima ou em outro navegador.";
  } catch {
    // Cai para a seleção do campo.
  }
  campo?.focus();
  campo?.select();
  let copiou: boolean;
  try {
    // eslint-disable-next-line @typescript-eslint/no-deprecated -- recurso de quem não tem a Clipboard API
    copiou = document.execCommand("copy");
  } catch {
    copiou = false;
  }
  return copiou ? "Convite copiado." : "O link está selecionado. Copie com Ctrl+C (ou ⌘+C).";
}

export function Convite({ link, aoCopiar }: PropsConvite) {
  const refLink = useRef<HTMLInputElement>(null);
  return (
    <section className={`convite ${TELA}`} id="convite" aria-labelledby="convite-titulo">
      <h2 id="convite-titulo" className={SUBTITULO}>
        Convite
      </h2>
      <div className={CAMPO}>
        <label className={ROTULO} htmlFor="link-convite">
          Link para a outra pessoa
        </label>
        <input
          ref={refLink}
          className={ENTRADA_DE_TEXTO}
          id="link-convite"
          type="text"
          readOnly
          spellCheck={false}
          aria-describedby="convite-dica"
          value={link}
        />
      </div>
      <button
        type="button"
        className={BOTAO_SECUNDARIO_SEM_MARGEM}
        id="copiar"
        onClick={() => {
          void copiar(link, refLink.current).then(aoCopiar);
        }}
      >
        Copiar convite
      </button>
      <p className={DICA} id="convite-dica">
        Para testar sozinho, abra o link em uma janela anônima ou em outro navegador: cada janela é uma pessoa. O
        convite vale para uma entrada.
      </p>
    </section>
  );
}
