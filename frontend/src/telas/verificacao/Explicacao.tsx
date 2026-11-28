import { useState, type ReactElement } from "react";
import { BOTAO_PRIMARIO, DICA, seDisponivel } from "../../componentes/estilos";
import { VERIFICACAO_DE_IDADE } from "./textos";

const ID_DO_CONSENTIMENTO = "verificacao-consentimento";
const ID_DO_MOTIVO = "verificacao-motivo";

const PARAGRAFO = "m-0 text-texto-2 text-[1.0625rem] leading-[1.55]";
const LINK_EM_TEXTO = "text-ambar underline underline-offset-4 hover:text-ambar-claro [overflow-wrap:anywhere]";

interface PropsDaExplicacao {
  tentativas: number;
  /** O e-mail do encarregado: o caminho de quem não pode olhar para a tela piscando. */
  emailDoEncarregado: string;
  /** Câmera, WebAssembly e contexto seguro; sem eles a selfie não abre. */
  temSuporte: boolean;
  aoComecar: () => void;
}

/**
 * Antes da selfie: para que ela serve, quem a confere e o que fica guardado, as dicas, o aviso de luz
 * piscando (com o caminho manual pelo e-mail do encarregado) e o consentimento. O botão fica no foco e
 * na leitura mesmo indisponível, e a linha logo abaixo diz o que falta.
 */
export function Explicacao({ tentativas, emailDoEncarregado, temSuporte, aoComecar }: PropsDaExplicacao): ReactElement {
  const [consentiu, setConsentiu] = useState(false);
  const motivo = !temSuporte
    ? VERIFICACAO_DE_IDADE.semSuporte
    : !consentiu
      ? VERIFICACAO_DE_IDADE.semConsentimento
      : null;
  const indisponivel = motivo !== null;

  return (
    <div className="mt-(--e-5) grid gap-(--e-5)">
      <p className="m-0 text-marfim text-[1.125rem] leading-[1.5]">{VERIFICACAO_DE_IDADE.paraQue}</p>
      <p className={PARAGRAFO}>{VERIFICACAO_DE_IDADE.usoDaImagem}</p>
      <ul className="m-0 p-0 list-none grid gap-(--e-2)">
        {VERIFICACAO_DE_IDADE.dicas.map((dica) => (
          <li
            key={dica}
            className="relative pl-(--e-5) text-texto-2 leading-[1.45] before:absolute before:left-0 before:top-[0.6em] before:size-[7px] before:rounded-full before:bg-verde-agua"
          >
            {dica}
          </li>
        ))}
      </ul>
      <p className="m-0 border-l-[3px] border-ambar pl-(--e-4) text-marfim leading-[1.5]">
        {VERIFICACAO_DE_IDADE.avisoDeLuzAntes}
        <a href={`mailto:${emailDoEncarregado}`} className={LINK_EM_TEXTO}>
          {emailDoEncarregado}
        </a>
        {VERIFICACAO_DE_IDADE.avisoDeLuzDepois}
      </p>
      <div className="flex items-start gap-(--e-3)">
        <input
          className="mt-[2px] size-6 flex-none accent-ambar cursor-pointer"
          id={ID_DO_CONSENTIMENTO}
          type="checkbox"
          checked={consentiu}
          onChange={(evento) => {
            setConsentiu(evento.target.checked);
          }}
        />
        <label className="leading-[1.45] cursor-pointer" htmlFor={ID_DO_CONSENTIMENTO}>
          {VERIFICACAO_DE_IDADE.consentimento}
        </label>
      </div>
      <div className="grid gap-(--e-2)">
        <p className="m-0 text-texto-3 text-[0.9375rem]">{VERIFICACAO_DE_IDADE.tentativas(tentativas)}</p>
        <button
          type="button"
          className={`w-full ${BOTAO_PRIMARIO}`}
          aria-disabled={indisponivel ? true : undefined}
          aria-describedby={indisponivel ? ID_DO_MOTIVO : undefined}
          onClick={seDisponivel(indisponivel, aoComecar)}
        >
          {VERIFICACAO_DE_IDADE.comecar}
        </button>
        <p className={DICA} id={ID_DO_MOTIVO}>
          {motivo ?? ""}
        </p>
      </div>
    </div>
  );
}
