import { useState, type ReactElement } from "react";

export type TamanhoDaFoto = "grande" | "medio";

interface Props {
  url: string | null;
  alt: string;
  tamanho: TamanhoDaFoto;
  /** `eager` só no cartão principal da tela; no resto, a foto espera chegar perto da rolagem. */
  carregamento?: "lazy" | "eager";
}

const MOLDURA = "block aspect-square shrink-0 rounded-(--raio) border border-noite-700 overflow-hidden";

const TAMANHOS: Record<TamanhoDaFoto, string> = {
  grande: "w-full max-w-[240px]",
  medio: "w-24",
};

/** Busto sem gênero (cabeça e ombros), no lugar da foto que não há ou não abriu. */
function Silhueta({ tamanho }: { tamanho: TamanhoDaFoto }): ReactElement {
  return (
    <svg
      role="img"
      aria-label="Sem foto"
      viewBox="0 0 96 96"
      className={`${MOLDURA} ${TAMANHOS[tamanho]} bg-noite-800 text-ceu-400 forced-colors:text-[CanvasText]`}
    >
      <circle cx="48" cy="38" r="16" fill="currentColor" />
      <path d="M18 96c0-19 13.4-32 30-32s30 13 30 32Z" fill="currentColor" />
    </svg>
  );
}

/**
 * A foto quadrada ou, sem ela, a silhueta. Se a imagem falhar ao carregar (recusada, apagada, rede),
 * vira silhueta também; uma URL nova tenta de novo.
 */
export function FotoOuSilhueta({ url, alt, tamanho, carregamento = "lazy" }: Props): ReactElement {
  const [urlQueFalhou, setUrlQueFalhou] = useState<string | null>(null);
  if (url === null || url === urlQueFalhou) {
    return <Silhueta tamanho={tamanho} />;
  }
  return (
    <img
      src={url}
      alt={alt}
      loading={carregamento}
      decoding="async"
      onError={() => {
        setUrlQueFalhou(url);
      }}
      className={`${MOLDURA} ${TAMANHOS[tamanho]} h-auto bg-noite-800 object-cover`}
    />
  );
}
