import { useMutation, useQuery } from "@tanstack/react-query";
import { useRef, useState, type ReactElement, type SubmitEvent } from "react";
import { obterConfiguracao, pedirLink } from "../../api/conta";
import { ErroHttp, novaChaveDeIdempotencia } from "../../api/http";
import {
  BOTAO_PRIMARIO,
  BOTAO_SECUNDARIO_SEM_MARGEM,
  CAMPO,
  DICA,
  ENTRADA_DE_TEXTO,
  ROTULO,
  TEXTO_SECUNDARIO,
} from "../../componentes/estilos";
import { carregarTurnstile, Turnstile, type ApiTurnstile, type ControleDoTurnstile } from "../../componentes/Turnstile";
import { ENTRAR, textoDeEspera } from "./textos";

interface PropsDeEntrar {
  carregarTurnstile?: () => Promise<ApiTurnstile>;
}

interface PedidoDeLink {
  email: string;
  token: string;
  chave: string;
}

/** Uma repetição só, e só quando o primeiro envio pode não ter chegado ao fim. */
const REPETICOES_AUTOMATICAS = 1;

function valeRepetir(falhas: number, erro: Error): boolean {
  return (
    falhas < REPETICOES_AUTOMATICAS &&
    erro instanceof ErroHttp &&
    (erro.status === 0 || erro.codigo === "REQUISICAO_EM_ANDAMENTO")
  );
}

/** Espera o Retry-After do 409 (1 s); sem ele (falha de rede), espera o mesmo 1 s. */
function esperaAntesDeRepetir(_falhas: number, erro: Error): number {
  const segundos = erro instanceof ErroHttp ? (erro.tentarDeNovoEmSegundos ?? 1) : 1;
  return segundos * 1000;
}

function textoDoErro(erro: Error): string {
  if (erro instanceof ErroHttp) {
    if (erro.status === 429) {
      return textoDeEspera(erro.tentarDeNovoEmSegundos ?? 0);
    }
    if (erro.status === 422 && erro.mensagem) {
      return erro.mensagem;
    }
  }
  return ENTRAR.falhaNoEnvio;
}

function erroDoEmail(erro: Error | null): boolean {
  return erro instanceof ErroHttp && erro.codigo.startsWith("EMAIL_");
}

/**
 * Pede o link mágico. O envio só fica disponível com um token do Turnstile; cada clique leva uma
 * chave de idempotência nova, e a repetição automática (409 em andamento ou falha de rede) reusa a
 * chave e o token. Toda resposta final gasta o token: o widget reinicia e pede outro.
 */
export function Entrar({ carregarTurnstile: carregar = carregarTurnstile }: PropsDeEntrar): ReactElement {
  const configuracao = useQuery({
    queryKey: ["conta", "configuracao"],
    queryFn: obterConfiguracao,
    staleTime: Infinity,
  });
  const turnstile = useRef<ControleDoTurnstile>(null);
  const campoDoEmail = useRef<HTMLInputElement>(null);
  const [token, setToken] = useState<string | null>(null);
  const [widgetFalhou, setWidgetFalhou] = useState(false);
  // Trava síncrona: o isPending só chega no render seguinte, e um duplo toque nessa janela mandaria
  // outro pedido com o mesmo token (já gasto) e chave nova.
  const enviando = useRef(false);

  const pedido = useMutation({
    mutationFn: ({ email, token: tokenVerificacao, chave }: PedidoDeLink) => pedirLink(email, tokenVerificacao, chave),
    retry: valeRepetir,
    retryDelay: esperaAntesDeRepetir,
    // Sem pausa fora do ar: a falha de rede vira a repetição, e depois o aviso.
    networkMode: "always",
    onError: (erro) => {
      if (erroDoEmail(erro)) {
        campoDoEmail.current?.focus();
      }
    },
    onSettled: () => {
      enviando.current = false;
      setToken(null);
      turnstile.current?.reiniciar();
    },
  });

  const verificacaoFalhou = widgetFalhou || configuracao.isError;
  const indisponivel = token === null || pedido.isPending;
  const emailRecusado = erroDoEmail(pedido.error);

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (token === null || enviando.current) {
      return;
    }
    enviando.current = true;
    const email = campoDoEmail.current?.value.trim() ?? "";
    pedido.mutate({ email, token, chave: novaChaveDeIdempotencia() });
  }

  /** Sair do aviso monta um widget novo; o carregador tenta o script de novo se ele não veio. */
  function tentarDeNovo() {
    setWidgetFalhou(false);
    if (configuracao.isError) {
      void configuracao.refetch();
    }
  }

  return (
    <>
      <title>{ENTRAR.tituloDaAba}</title>
      <h1 className="m-0 font-titulo font-semibold text-[2rem] leading-[1.15] tracking-[-0.01em]">{ENTRAR.titulo}</h1>
      <form className="mt-(--e-6) grid gap-(--e-4)" onSubmit={enviar}>
        <div className={CAMPO}>
          <label className={ROTULO} htmlFor="entrar-email">
            {ENTRAR.rotuloDoEmail}
          </label>
          <input
            ref={campoDoEmail}
            className={ENTRADA_DE_TEXTO}
            id="entrar-email"
            name="email"
            type="email"
            autoComplete="email"
            spellCheck={false}
            required
            aria-describedby={emailRecusado ? "entrar-erro" : undefined}
            aria-invalid={emailRecusado ? true : undefined}
          />
        </div>
        <div className="min-h-[65px]">
          {verificacaoFalhou ? (
            <div className="grid gap-(--e-2)">
              <p className={TEXTO_SECUNDARIO} id="entrar-falha-verificacao">
                {ENTRAR.falhaDaVerificacao}
              </p>
              <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={tentarDeNovo}>
                {ENTRAR.tentarDeNovo}
              </button>
            </div>
          ) : (
            configuracao.data && (
              <Turnstile
                ref={turnstile}
                chaveDoSite={configuracao.data.chaveDoSiteTurnstile}
                carregar={carregar}
                aoObterToken={setToken}
                aoExpirar={() => {
                  setToken(null);
                }}
                aoFalhar={() => {
                  setToken(null);
                  setWidgetFalhou(true);
                }}
              />
            )
          )}
        </div>
        <button
          type="submit"
          className={BOTAO_PRIMARIO}
          aria-disabled={indisponivel ? true : undefined}
          aria-describedby={
            token === null ? (verificacaoFalhou ? "entrar-falha-verificacao" : "entrar-motivo") : undefined
          }
          onClick={(evento) => {
            // Indisponível de verdade: nem a validação nativa do campo dispara.
            if (indisponivel) {
              evento.preventDefault();
            }
          }}
        >
          {pedido.isPending ? ENTRAR.enviando : ENTRAR.enviar}
        </button>
        {token === null && !verificacaoFalhou && (
          // Com a mensagem de sucesso na tela, a dica sai da vista, mas segue como motivo do botão.
          <p className={pedido.isSuccess ? "sr-only" : DICA} id="entrar-motivo">
            {ENTRAR.aguardeAVerificacao}
          </p>
        )}
      </form>
      <p className="mt-(--e-5) mb-0 text-[1.0625rem] leading-[1.5] text-marfim" role="status">
        {pedido.isSuccess ? pedido.data.mensagem : ""}
      </p>
      <p className="mt-(--e-2) mb-0 text-[0.9375rem] leading-[1.45] text-coral" id="entrar-erro" role="alert">
        {pedido.isError ? textoDoErro(pedido.error) : ""}
      </p>
    </>
  );
}
