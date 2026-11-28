import { useEffect, useId, useRef, type ReactElement, type ReactNode } from "react";
import { BOTAO_SECUNDARIO_SEM_MARGEM, LINK_DE_ACAO, TITULO_DE_RESULTADO } from "../../componentes/estilos";
import type { TipoDeDesfecho } from "./etapas";
import { VERIFICACAO_DE_IDADE } from "./textos";

const PARAGRAFO = "m-0 text-marfim text-[1.0625rem] leading-[1.5]";
const LINK_EM_TEXTO = "text-ambar underline underline-offset-4 hover:text-ambar-claro [overflow-wrap:anywhere]";
const CONTAGEM = "m-0 text-texto-3 text-[0.9375rem]";

interface PropsDoTitulo {
  titulo: string;
  /** false quando a página já abre neste desfecho: o foco fica onde o navegador o pôs. */
  focar?: boolean;
  children?: ReactNode;
}

/**
 * O fim de um trecho da verificação: o título recebe o foco ao montar, para o leitor de tela começar
 * pelo que aconteceu. A página não devolve o foco ao h1 quando a etapa tem este título.
 */
export function Desfecho({ titulo, focar = true, children }: PropsDoTitulo): ReactElement {
  const idDoTitulo = useId();
  const cabecalho = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (focar) {
      cabecalho.current?.focus();
    }
  }, [focar]);
  return (
    <section className="mt-(--e-5) grid gap-(--e-4)" aria-labelledby={idDoTitulo}>
      <h2 id={idDoTitulo} ref={cabecalho} tabIndex={-1} className={TITULO_DE_RESULTADO}>
        {titulo}
      </h2>
      {children}
    </section>
  );
}

/** Página inteira: o terraço não é rota da casca. */
export function VoltarAoTerraco(): ReactElement {
  return (
    <a href="/" className={`justify-self-start ${LINK_DE_ACAO}`}>
      {VERIFICACAO_DE_IDADE.voltarAoTerraco}
    </a>
  );
}

/** Com tentativas, mostra quantas sobram e o "Tentar de novo"; sem nenhuma, o limite do dia e a saída. */
export function Repeticao({ tentativas, aoRepetir }: { tentativas: number; aoRepetir: () => void }): ReactElement {
  if (tentativas === 0) {
    return (
      <>
        {/* Anunciado: depois de uma falha, o foco fica no h1 e só o alerta seria lido. */}
        <p role="status" className={PARAGRAFO}>
          {VERIFICACAO_DE_IDADE.limiteDoDia}
        </p>
        <VoltarAoTerraco />
      </>
    );
  }
  return (
    <>
      <p className={CONTAGEM}>{VERIFICACAO_DE_IDADE.tentativas(tentativas)}</p>
      <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={aoRepetir}>
        {VERIFICACAO_DE_IDADE.tentarDeNovo}
      </button>
    </>
  );
}

interface PropsDoDesfecho {
  tipo: Exclude<TipoDeDesfecho, "sessaoInvalida">;
  focar: boolean;
  tentativas: number;
  emailDoEncarregado: string;
  aoRepetir: () => void;
}

/** Os desfechos com título: a aprovação, o REPETIR, a análise e o menor de idade. */
export function DesfechoDaVerificacao({
  tipo,
  focar,
  tentativas,
  emailDoEncarregado,
  aoRepetir,
}: PropsDoDesfecho): ReactElement {
  switch (tipo) {
    case "aprovada":
      return (
        <Desfecho titulo={VERIFICACAO_DE_IDADE.aprovada.titulo} focar={focar}>
          <p className={PARAGRAFO}>{VERIFICACAO_DE_IDADE.aprovada.texto}</p>
          <a href="/" className={`justify-self-start ${LINK_DE_ACAO}`}>
            {VERIFICACAO_DE_IDADE.continuar}
          </a>
        </Desfecho>
      );
    case "repetir":
      return (
        <Desfecho titulo={VERIFICACAO_DE_IDADE.repetir.titulo} focar={focar}>
          <p className={PARAGRAFO}>{VERIFICACAO_DE_IDADE.repetir.texto}</p>
          <Repeticao tentativas={tentativas} aoRepetir={aoRepetir} />
        </Desfecho>
      );
    case "analise":
      return (
        <Desfecho titulo={VERIFICACAO_DE_IDADE.analise.titulo} focar={focar}>
          <p className={PARAGRAFO}>
            {VERIFICACAO_DE_IDADE.analise.antes}
            <a href={`mailto:${emailDoEncarregado}`} className={LINK_EM_TEXTO}>
              {emailDoEncarregado}
            </a>
            {VERIFICACAO_DE_IDADE.analise.depois}
          </p>
          <VoltarAoTerraco />
        </Desfecho>
      );
    case "menor":
      return (
        <Desfecho titulo={VERIFICACAO_DE_IDADE.menor.titulo} focar={focar}>
          <p className={PARAGRAFO}>{VERIFICACAO_DE_IDADE.menor.texto}</p>
          <VoltarAoTerraco />
        </Desfecho>
      );
  }
}
