// O detector de prova de vida da AWS (Amplify). É o único arquivo que importa o Amplify (regra do
// ESLint), e só o CapturaDeVida o carrega, por import(): o pacote fica no chunk
// verificacao-amplify, fora do JS inicial e do chunk da rota. Sem binaryPath nem faceModelUrl: o
// wasm e o modelo vêm das URLs padrão do pacote, que a CSP da /verificacao libera e o
// cadeiaDeSuprimentos.test.ts pina.

import { FaceLivenessDetectorCore, type ErrorState } from "@aws-amplify/ui-react-liveness";
import "@aws-amplify/ui-react-liveness/styles.css";
import type { ReactElement } from "react";
import type { PropsDoDetector } from "./CapturaDeVida";
import type { FalhaDoDetector } from "./etapas";
import { TEXTOS_DO_DETECTOR } from "./textosDoDetector";

/** A região da sessão de prova de vida é fixa (SCP da conta AWS); o servidor confere o mesmo valor. */
const REGIAO = "us-east-1";

/** Exaustivo sobre os estados de erro do pacote: um estado novo quebra a compilação. */
export function falhaDoEstado(estado: ErrorState): FalhaDoDetector {
  switch (estado) {
    case "CAMERA_ACCESS_ERROR":
    case "DEFAULT_CAMERA_NOT_FOUND_ERROR":
      return "camera";
    case "TIMEOUT":
    case "FRESHNESS_TIMEOUT":
      return "tempo";
    case "FACE_DISTANCE_ERROR":
      return "rosto";
    case "MULTIPLE_FACES_ERROR":
      return "outraPessoa";
    case "CONNECTION_TIMEOUT":
      return "conexao";
    case "SERVER_ERROR":
      return "servico";
    case "CAMERA_FRAMERATE_ERROR":
    case "RUNTIME_ERROR":
      return "aparelho";
    case "MOBILE_LANDSCAPE_ERROR":
      return "orientacao";
  }
}

export default function DetectorAmplify({ sessao, aoConcluir, aoFalhar, aoCancelar }: PropsDoDetector): ReactElement {
  return (
    <FaceLivenessDetectorCore
      sessionId={sessao.sessaoId}
      region={REGIAO}
      disableStartScreen
      displayText={TEXTOS_DO_DETECTOR}
      config={{
        credentialProvider: () => Promise.resolve(sessao.credencial),
        systemClockOffset: sessao.desvioDoRelogioMs,
      }}
      onAnalysisComplete={() => {
        aoConcluir();
        return Promise.resolve();
      }}
      onUserCancel={aoCancelar}
      onError={({ state }) => {
        aoFalhar(falhaDoEstado(state));
      }}
    />
  );
}
