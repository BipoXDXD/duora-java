// A selfie em vídeo: escolhe o detector pelo modo que o servidor mandou na sessão (D-F2) e o carrega
// sob demanda. O do Amplify (vários MB, no chunk verificacao-amplify) só baixa aqui, na captura; o
// falso só baixa com modo "falso". Nenhum gancho global decide o detector.

import { Component, lazy, Suspense, useId, type ReactElement, type ReactNode } from "react";
import type { SessaoDeCaptura } from "../../api/verificacao";
import type { FalhaDoDetector } from "./etapas";
import { VERIFICACAO_DE_IDADE } from "./textos";

export interface PropsDoDetector {
  sessao: SessaoDeCaptura;
  aoConcluir: () => void;
  aoFalhar: (falha: FalhaDoDetector) => void;
  aoCancelar: () => void;
}

const DetectorAmplify = lazy(() => import("./DetectorAmplify"));
const DetectorFalso = lazy(() => import("./DetectorFalso"));

const TITULO = "m-0 font-titulo font-semibold text-[1.375rem] leading-[1.2]";
const ESPERA = "m-0 text-texto-2 text-[1.0625rem] leading-[1.5]";

interface PropsDoLimite {
  aoFalhar: () => void;
  children: ReactNode;
}

/**
 * O chunk que não baixa (rede caiu, deploy novo apagou o arquivo) ou o detector que quebra ao
 * renderizar: a página recebe a falha "carregamento" e mostra o caminho de volta. Avisa uma vez só.
 */
class LimiteDoDetector extends Component<PropsDoLimite, { falhou: boolean }> {
  override state = { falhou: false };

  static getDerivedStateFromError(): { falhou: boolean } {
    return { falhou: true };
  }

  override componentDidCatch(): void {
    this.props.aoFalhar();
  }

  override render(): ReactNode {
    return this.state.falhou ? null : this.props.children;
  }
}

export function CapturaDeVida(props: PropsDoDetector): ReactElement {
  const idDoTitulo = useId();
  const Detector = props.sessao.modo === "falso" ? DetectorFalso : DetectorAmplify;
  const { aoFalhar } = props;
  return (
    <section aria-labelledby={idDoTitulo} className="mt-(--e-5) grid gap-(--e-4)">
      <h2 id={idDoTitulo} className={TITULO}>
        {VERIFICACAO_DE_IDADE.selfieEmVideo}
      </h2>
      <LimiteDoDetector
        aoFalhar={() => {
          aoFalhar("carregamento");
        }}
      >
        <Suspense
          fallback={
            <p role="status" className={ESPERA}>
              {VERIFICACAO_DE_IDADE.abrindoCamera}
            </p>
          }
        >
          <Detector {...props} />
        </Suspense>
      </LimiteDoDetector>
    </section>
  );
}
