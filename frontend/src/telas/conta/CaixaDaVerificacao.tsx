import type { ReactElement } from "react";
import { BOTAO_SECUNDARIO_SEM_MARGEM, TEXTO_SECUNDARIO } from "../../componentes/estilos";
import { carregarTurnstile, Turnstile, type ApiTurnstile } from "../../componentes/Turnstile";
import { VERIFICACAO } from "./textos";
import type { VerificacaoHumana } from "./useVerificacaoHumana";

interface PropsDaCaixa {
  verificacao: VerificacaoHumana;
  /** O id do aviso de falha, que o botão de envio usa como descrição. */
  idDaFalha: string;
  carregar?: () => Promise<ApiTurnstile>;
}

/**
 * O lugar do widget do Turnstile no formulário, com altura reservada: o widget quando a configuração
 * chega e, se ele ou a configuração falhar, o aviso com Tentar de novo.
 */
export function CaixaDaVerificacao({
  verificacao,
  idDaFalha,
  carregar = carregarTurnstile,
}: PropsDaCaixa): ReactElement {
  const { chaveDoSite, falhou, turnstile, aoObterToken, aoExpirar, aoFalhar, tentarDeNovo } = verificacao;
  return (
    <div className="min-h-[65px]">
      {falhou ? (
        <div className="grid gap-(--e-2)">
          <p className={TEXTO_SECUNDARIO} id={idDaFalha}>
            {VERIFICACAO.falha}
          </p>
          <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={tentarDeNovo}>
            {VERIFICACAO.tentarDeNovo}
          </button>
        </div>
      ) : (
        chaveDoSite !== null && (
          <Turnstile
            ref={turnstile}
            chaveDoSite={chaveDoSite}
            carregar={carregar}
            aoObterToken={aoObterToken}
            aoExpirar={aoExpirar}
            aoFalhar={aoFalhar}
          />
        )
      )}
    </div>
  );
}
