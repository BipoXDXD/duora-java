// Widget do Cloudflare Turnstile (render explícito). Só a casca de conta o usa: o script vem de
// challenges.cloudflare.com, liberado na CSP por CabecalhosDeSeguranca.

import { useEffect, useEffectEvent, useImperativeHandle, useRef, type ReactElement, type Ref } from "react";

const URL_DO_SCRIPT = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

export interface OpcoesDoWidget {
  sitekey: string;
  callback: (token: string) => void;
  "expired-callback": () => void;
  "error-callback": () => void;
  language: "pt-br";
  theme: "dark";
}

/** A parte da API global `turnstile` que o widget usa. */
export interface ApiTurnstile {
  render(el: HTMLElement, opcoes: OpcoesDoWidget): string;
  reset(id: string): void;
  remove(id: string): void;
}

interface JanelaComTurnstile {
  turnstile?: ApiTurnstile;
}

/**
 * Carregador com um único <script> por documento: as chamadas seguintes reaproveitam a mesma
 * promessa. Se o script falhar (rede, bloqueio), ele sai do documento e a próxima chamada tenta de
 * novo.
 */
export function criarCarregadorDoTurnstile(documento: Document): () => Promise<ApiTurnstile> {
  let carregando: Promise<ApiTurnstile> | null = null;
  return () => {
    carregando ??= new Promise<ApiTurnstile>((resolver, rejeitar) => {
      const script = documento.createElement("script");
      script.src = URL_DO_SCRIPT;
      script.async = true;
      script.addEventListener("load", () => {
        const api = (documento.defaultView as JanelaComTurnstile | null)?.turnstile;
        if (api) {
          resolver(api);
        } else {
          rejeitar(new Error("o script do Turnstile carregou sem a API turnstile"));
        }
      });
      script.addEventListener("error", () => {
        script.remove();
        carregando = null;
        rejeitar(new Error("o script do Turnstile não carregou"));
      });
      documento.head.append(script);
    });
    return carregando;
  };
}

export const carregarTurnstile = criarCarregadorDoTurnstile(document);

export interface ControleDoTurnstile {
  /** Pede um token novo, depois que o anterior foi gasto ou recusado. */
  reiniciar(): void;
}

interface PropsDoTurnstile {
  chaveDoSite: string;
  aoObterToken: (t: string) => void;
  aoExpirar: () => void;
  /** Erro do desafio ou script que não carregou. */
  aoFalhar: () => void;
  carregar?: () => Promise<ApiTurnstile>;
  ref?: Ref<ControleDoTurnstile>;
}

interface WidgetDesenhado {
  api: ApiTurnstile;
  id: string;
}

export function Turnstile({
  chaveDoSite,
  aoObterToken,
  aoExpirar,
  aoFalhar,
  carregar = carregarTurnstile,
  ref,
}: PropsDoTurnstile): ReactElement {
  const elemento = useRef<HTMLDivElement>(null);
  const widget = useRef<WidgetDesenhado | null>(null);

  // Os avisos leem as props mais recentes sem redesenhar o widget a cada render do pai.
  const avisarToken = useEffectEvent((token: string) => {
    aoObterToken(token);
  });
  const avisarExpirado = useEffectEvent(() => {
    aoExpirar();
  });
  const avisarFalha = useEffectEvent(() => {
    aoFalhar();
  });

  useImperativeHandle(
    ref,
    () => ({
      reiniciar() {
        const atual = widget.current;
        if (atual) {
          atual.api.reset(atual.id);
        }
      },
    }),
    [],
  );

  useEffect(() => {
    let montado = true;
    carregar().then(
      (api) => {
        if (!montado || !elemento.current) {
          return;
        }
        const id = api.render(elemento.current, {
          sitekey: chaveDoSite,
          callback: (token) => {
            avisarToken(token);
          },
          "expired-callback": () => {
            avisarExpirado();
          },
          "error-callback": () => {
            avisarFalha();
          },
          language: "pt-br",
          theme: "dark",
        });
        widget.current = { api, id };
      },
      () => {
        if (montado) {
          avisarFalha();
        }
      },
    );
    return () => {
      montado = false;
      const atual = widget.current;
      if (atual) {
        atual.api.remove(atual.id);
        widget.current = null;
      }
    };
  }, [carregar, chaveDoSite]);

  return <div ref={elemento} />;
}
