import { useMutation } from "@tanstack/react-query";
import { useRef, type ReactElement, type SubmitEvent } from "react";
import { pedirLink } from "../../api/conta";
import { ErroHttp, novaChaveDeIdempotencia } from "../../api/http";
import { BOTAO_PRIMARIO, CAMPO, DICA, ENTRADA_DE_TEXTO, ROTULO, TITULO_DA_CASCA } from "../../componentes/estilos";
import type { ApiTurnstile } from "../../componentes/Turnstile";
import { CaixaDaVerificacao } from "./CaixaDaVerificacao";
import { ENTRAR, textoDeEspera, VERIFICACAO } from "./textos";
import { useVerificacaoHumana } from "./useVerificacaoHumana";

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
export function Entrar({ carregarTurnstile: carregar }: PropsDeEntrar): ReactElement {
  const verificacao = useVerificacaoHumana();
  const campoDoEmail = useRef<HTMLInputElement>(null);

  const pedido = useMutation({
    mutationFn: ({ email, token: tokenVerificacao, chave }: PedidoDeLink) => pedirLink(email, tokenVerificacao, chave),
    retry: valeRepetir,
    retryDelay: esperaAntesDeRepetir,
    onError: (erro) => {
      if (erroDoEmail(erro)) {
        campoDoEmail.current?.focus();
      }
    },
    onSettled: verificacao.liberar,
  });

  const { token, falhou: verificacaoFalhou } = verificacao;
  const indisponivel = token === null || pedido.isPending;
  const emailRecusado = erroDoEmail(pedido.error);

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    const tokenDoEnvio = verificacao.tomar();
    if (tokenDoEnvio === null) {
      return;
    }
    const email = campoDoEmail.current?.value.trim() ?? "";
    pedido.mutate({ email, token: tokenDoEnvio, chave: novaChaveDeIdempotencia() });
  }

  return (
    <>
      <title>{ENTRAR.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{ENTRAR.titulo}</h1>
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
        <CaixaDaVerificacao verificacao={verificacao} idDaFalha="entrar-falha-verificacao" carregar={carregar} />
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
            {VERIFICACAO.aguarde}
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
