import { useQuery } from "@tanstack/react-query";
import { useRef, useState, type RefObject } from "react";
import { obterConfiguracao } from "../../api/conta";
import type { ControleDoTurnstile } from "../../componentes/Turnstile";

/** O estado da verificação humana (Turnstile) de um formulário, e o que a tela faz com ele. */
export interface VerificacaoHumana {
  /** A chave pública do Turnstile; null até a configuração chegar. */
  chaveDoSite: string | null;
  /** O token do desafio resolvido; null antes dele, depois de gasto ou expirado. */
  token: string | null;
  /** O widget ou a configuração falhou: a tela mostra o aviso no lugar do widget. */
  falhou: boolean;
  turnstile: RefObject<ControleDoTurnstile | null>;
  aoObterToken: (token: string) => void;
  aoExpirar: () => void;
  aoFalhar: () => void;
  /** Sai do aviso: monta um widget novo e, se foi a configuração que falhou, lê de novo. */
  tentarDeNovo: () => void;
  /** Há token e nenhum envio em curso. */
  livre: () => boolean;
  /** Fecha a trava e entrega o token, se livre; senão, null. */
  tomar: () => string | null;
  /** Depois de toda resposta final: abre a trava, esquece o token gasto e pede outro ao widget. */
  liberar: () => void;
}

/**
 * A verificação humana das telas que enviam sem sessão (entrar, denunciar): a configuração com a
 * chave do site, o token do widget e a trava síncrona contra duplo envio. A trava é um ref: o
 * isPending só chega no render seguinte, e um duplo toque nessa janela mandaria outro pedido com o
 * mesmo token (já gasto).
 */
export function useVerificacaoHumana(): VerificacaoHumana {
  const configuracao = useQuery({
    queryKey: ["conta", "configuracao"],
    queryFn: obterConfiguracao,
    staleTime: Infinity,
  });
  const turnstile = useRef<ControleDoTurnstile>(null);
  const [token, setToken] = useState<string | null>(null);
  const [widgetFalhou, setWidgetFalhou] = useState(false);
  const enviando = useRef(false);

  function livre(): boolean {
    return token !== null && !enviando.current;
  }

  return {
    chaveDoSite: configuracao.data?.chaveDoSiteTurnstile ?? null,
    token,
    falhou: widgetFalhou || configuracao.isError,
    turnstile,
    aoObterToken: setToken,
    aoExpirar: () => {
      setToken(null);
    },
    aoFalhar: () => {
      setToken(null);
      setWidgetFalhou(true);
    },
    tentarDeNovo: () => {
      setWidgetFalhou(false);
      if (configuracao.isError) {
        void configuracao.refetch();
      }
    },
    livre,
    tomar: () => {
      if (!livre()) {
        return null;
      }
      enviando.current = true;
      return token;
    },
    liberar: () => {
      enviando.current = false;
      setToken(null);
      turnstile.current?.reiniciar();
    },
  };
}
