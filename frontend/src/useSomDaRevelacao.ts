// O som da passagem ao vivo para a noite: o acorde da revelação (só com o som ligado, o Som decide)
// e as notas de cada peça colocada que as declara, que tocam mesmo com o som desligado.

import { useEffect, useEffectEvent, useRef } from "react";
import type { Som } from "./cena/som";
import type { CenarioFront } from "./cenario/tipos";
import type { EstadoDaSessao } from "./sessao/sessao";
import { usePassagemAoVivo } from "./usePassagemAoVivo";
import { visaoDaTela } from "./useSessao";

/** Uma vez por passagem ao vivo; recarregar já na noite não toca nada. */
export function useSomDaRevelacao(
  som: Pick<Som, "revelacao" | "notas">,
  cenario: CenarioFront,
  estado: EstadoDaSessao,
): void {
  const passagemAoVivo = usePassagemAoVivo(estado);
  const pecas = visaoDaTela(estado)?.pecas ?? [];

  // Lido na hora do efeito: a visão da noite, sem refazer o efeito a cada consulta.
  const tocar = useEffectEvent(() => {
    som.revelacao();
    for (const { peca, lugar } of pecas) {
      if (lugar === null) {
        continue;
      }
      for (const efeito of cenario.revelacao(peca)) {
        if (efeito.tipo === "notas") {
          som.notas(cenario.audio(efeito.som), efeito.volume);
        }
      }
    }
  });

  // Ref, não estado: o efeito repetido do StrictMode não toca uma segunda vez.
  const tocou = useRef(false);
  useEffect(() => {
    if (!passagemAoVivo) {
      tocou.current = false;
      return;
    }
    if (!tocou.current) {
      tocou.current = true;
      tocar();
    }
  }, [passagemAoVivo]);
}
