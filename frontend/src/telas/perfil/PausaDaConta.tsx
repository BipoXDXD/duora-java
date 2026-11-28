import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement, type Ref, type RefObject } from "react";
import { pausarConta, voltarAAparecer, type MeuPerfil } from "../../api/perfil";
import { BOTAO_SECUNDARIO_SEM_MARGEM, seDisponivel, TEXTO_SECUNDARIO } from "../../componentes/estilos";
import { consultaDoMeuPerfil } from "../../rotas/consultas";
import { PAUSA } from "./textos";

const ID_DO_TITULO = "perfil-titulo-pausa";

const TITULO_DE_SECAO = "m-0 font-titulo font-semibold text-[1.375rem] leading-[1.2]";

/**
 * A faixa do topo de Meu perfil enquanto a conta está pausada. Fica sempre na árvore (vazia, some
 * do layout) para o leitor de tela anunciar quando ela aparece.
 */
export function FaixaDaPausa({ pausada, ref }: { pausada: boolean; ref?: Ref<HTMLParagraphElement> }): ReactElement {
  return (
    <p
      ref={ref}
      tabIndex={-1}
      role="status"
      className="mt-(--e-4) mb-0 px-(--e-4) py-(--e-3) border-l-4 border-ambar rounded-(--raio) bg-noite-950 text-marfim text-[1.0625rem] leading-[1.5] empty:hidden focus:not-focus-visible:[outline:none]"
    >
      {pausada ? PAUSA.faixa : ""}
    </p>
  );
}

interface Props {
  pausada: boolean;
  /** A faixa recebe o foco ao pausar; ao voltar, o foco fica no botão. */
  faixa: RefObject<HTMLParagraphElement | null>;
}

/** Pausar a conta e voltar a aparecer, sem diálogo: as duas ações se desfazem com um toque. */
export function PausaDaConta({ pausada, faixa }: Props): ReactElement {
  const clienteDeConsulta = useQueryClient();
  const [falhou, setFalhou] = useState(false);
  const botao = useRef<HTMLButtonElement>(null);
  // O estado em que o foco deve ir para o lugar, depois que o perfil mostrar a mudança.
  const focarQuando = useRef<boolean | null>(null);

  const mudar = useMutation({
    mutationFn: (pausar: boolean) => (pausar ? pausarConta() : voltarAAparecer()),
    onSuccess: (_, pausar) => {
      clienteDeConsulta.setQueryData<MeuPerfil>(consultaDoMeuPerfil.queryKey, (atual) =>
        atual === undefined ? atual : { ...atual, pausada: pausar },
      );
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDoMeuPerfil.queryKey, exact: true });
      focarQuando.current = pausar;
    },
    onError: () => {
      setFalhou(true);
    },
  });

  useEffect(() => {
    if (focarQuando.current === null || focarQuando.current !== pausada) {
      return;
    }
    focarQuando.current = null;
    (pausada ? faixa.current : botao.current)?.focus();
  });

  function alternar() {
    setFalhou(false);
    mudar.mutate(!pausada);
  }

  const pendente = mudar.isPending;
  const rotulo = pausada ? (pendente ? PAUSA.voltando : PAUSA.voltar) : pendente ? PAUSA.pausando : PAUSA.pausar;

  return (
    <section
      aria-labelledby={ID_DO_TITULO}
      className="mt-(--e-8) pt-(--e-6) border-t border-noite-700 grid gap-(--e-4)"
    >
      <h2 id={ID_DO_TITULO} className={TITULO_DE_SECAO}>
        {PAUSA.titulo}
      </h2>
      <p className={`${TEXTO_SECUNDARIO} leading-[1.5] max-w-[60ch]`}>{PAUSA.explicacao}</p>
      <button
        ref={botao}
        type="button"
        className={`${BOTAO_SECUNDARIO_SEM_MARGEM} text-marfim`}
        aria-disabled={pendente ? true : undefined}
        onClick={seDisponivel(pendente, alternar)}
      >
        {rotulo}
      </button>
      <p className="m-0 text-[0.9375rem] leading-[1.45] text-coral empty:hidden" role="alert">
        {falhou ? PAUSA.falha : ""}
      </p>
    </section>
  );
}
