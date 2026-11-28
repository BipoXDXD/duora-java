// O detector do modo falso (D-F2): só carrega quando o servidor manda modo "falso" na sessão. Sem
// câmera e sem wasm; a captura simulada segue o mesmo caminho da real (aoConcluir pede o resultado).

import type { ReactElement } from "react";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO_SEM_MARGEM } from "../../componentes/estilos";
import type { PropsDoDetector } from "./CapturaDeVida";

export const TEXTOS_DO_FALSO = {
  aviso: "Modo de teste: nenhuma câmera é usada.",
  simular: "Simular captura",
  cancelar: "Cancelar",
} as const;

export default function DetectorFalso({ aoConcluir, aoCancelar }: PropsDoDetector): ReactElement {
  return (
    <div className="grid gap-(--e-4)">
      <p className="m-0 text-texto-2 text-[1.0625rem] leading-[1.5]">{TEXTOS_DO_FALSO.aviso}</p>
      <button type="button" className={BOTAO_PRIMARIO} onClick={aoConcluir}>
        {TEXTOS_DO_FALSO.simular}
      </button>
      <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={aoCancelar}>
        {TEXTOS_DO_FALSO.cancelar}
      </button>
    </div>
  );
}
