import type { ReactElement } from "react";
import type { Conta } from "../../api/estadoDaConta";
import type { VersoesLegais } from "../../api/legal";
import type { SituacaoDaVerificacao } from "../../api/verificacao";
import { TITULO_DA_CASCA } from "../../componentes/estilos";
import { VERIFICACAO_DE_IDADE } from "./textos";

/** O que o loader da rota entrega: a conta aguardando verificação, a situação dela e as versões legais. */
export interface DadosDaVerificacao {
  conta: Conta;
  situacao: SituacaoDaVerificacao;
  legal: VersoesLegais;
}

/**
 * A verificação de idade, no documento próprio da /verificacao. O loader já leu a conta, a situação e
 * as versões legais: a espera da primeira carga é a da casca, e a falha de qualquer leitura vai para o
 * erro da casca.
 */
export function VerificacaoPagina(): ReactElement {
  return (
    <>
      <title>{VERIFICACAO_DE_IDADE.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{VERIFICACAO_DE_IDADE.titulo}</h1>
    </>
  );
}
