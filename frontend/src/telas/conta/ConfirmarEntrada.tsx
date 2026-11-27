import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, type ReactElement } from "react";
import { Link, useLoaderData, useNavigate } from "react-router";
import { confirmarEntrada } from "../../api/conta";
import type { Conta } from "../../api/estadoDaConta";
import { ErroHttp } from "../../api/http";
import { BOTAO_PRIMARIO, LINK_DE_ACAO, TITULO_DA_CASCA } from "../../componentes/estilos";
import { consultaDaConta } from "../../rotas/consultas";
import { CONFIRMAR, textoDeEspera } from "./textos";

interface PropsDeConfirmarEntrada {
  token: string | null;
  /** Saída da casca com recarga completa; o terraço ("/") não é rota do roteador. */
  navegarParaPagina?: (url: string) => void;
}

/** O que o loader da rota entrega: o token do link, tomado uma vez só. */
export interface DadosDaConfirmacao {
  token: string | null;
}

/** Para onde seguir depois da entrada: uma rota da casca ou uma página inteira. */
type Destino = { rota: string } | { pagina: string };

/** A sessão abriu, mas a leitura seguinte falhou: o token já foi gasto e não vale repetir o POST. */
class FalhaDepoisDaEntrada extends Error {
  constructor(cause: unknown) {
    super("A entrada foi confirmada, mas a conta não pôde ser lida", { cause });
    this.name = "FalhaDepoisDaEntrada";
  }
}

interface Recusa {
  texto: string;
  /** Repetível: o botão volta a valer. Final: o botão sai e a mensagem recebe o foco. */
  repetivel: boolean;
  saida: "pedirNovoLink" | "terraco" | null;
}

const CONTA_INDISPONIVEL = "CONTA_INDISPONIVEL";

function recusaDaContaIndisponivel(erro: ErroHttp): Recusa {
  return { texto: erro.mensagem ?? CONFIRMAR.contaIndisponivel, repetivel: false, saida: null };
}

/** Pelo código do contrato (C6); o que não tem código conhecido é falha sem resposta conclusiva. */
function recusaDo(erro: Error): Recusa {
  if (erro instanceof FalhaDepoisDaEntrada) {
    return erro.cause instanceof ErroHttp && erro.cause.codigo === CONTA_INDISPONIVEL
      ? recusaDaContaIndisponivel(erro.cause)
      : { texto: CONFIRMAR.falhaDepoisDaEntrada, repetivel: false, saida: "terraco" };
  }
  if (erro instanceof ErroHttp) {
    switch (erro.codigo) {
      case "LINK_INVALIDO":
        return { texto: erro.mensagem ?? CONFIRMAR.linkInvalido, repetivel: false, saida: "pedirNovoLink" };
      case CONTA_INDISPONIVEL:
        return recusaDaContaIndisponivel(erro);
      case "LIMITE_DE_TAXA":
        return { texto: textoDeEspera(erro.tentarDeNovoEmSegundos ?? 0), repetivel: true, saida: null };
    }
  }
  return { texto: CONFIRMAR.falha, repetivel: true, saida: null };
}

function destinoDa(conta: Conta | null): Destino {
  switch (conta?.estado) {
    case "PENDENTE":
      return { rota: "/cadastro" };
    case "AGUARDANDO_VERIFICACAO":
      return { rota: "/aguardando" };
    case "ATIVA":
      return { pagina: "/" };
    case undefined:
      // 204 logo depois do 200: o navegador não guardou o cookie da sessão.
      throw new FalhaDepoisDaEntrada(new Error("Sem sessão depois da confirmação"));
  }
}

/**
 * Troca o token do link mágico por uma sessão. O POST só sai no clique em Entrar: um leitor de e-mail
 * que pré-abre o link não gasta o token, que é de uso único. Depois do 200, quem já concluiu o
 * cadastro vai ao terraço; quem não concluiu segue para a tela do estado da conta.
 */
export function ConfirmarEntrada({
  token,
  navegarParaPagina = (url) => {
    location.assign(url);
  },
}: PropsDeConfirmarEntrada): ReactElement {
  const clienteDeConsulta = useQueryClient();
  const navegar = useNavigate();
  const mensagemFinal = useRef<HTMLParagraphElement>(null);
  // Trava síncrona: o isPending só chega no render seguinte, e um duplo clique nessa janela gastaria
  // o token num POST e receberia LINK_INVALIDO no outro.
  const enviando = useRef(false);

  const confirmacao = useMutation({
    mutationFn: async (tokenDoLink: string): Promise<Destino> => {
      const { cadastroConcluido } = await confirmarEntrada(tokenDoLink);
      if (cadastroConcluido) {
        return { pagina: "/" };
      }
      try {
        // Só a conta: ["conta", "configuracao"] não muda com a sessão.
        await clienteDeConsulta.invalidateQueries({ queryKey: consultaDaConta.queryKey, exact: true });
        return destinoDa(await clienteDeConsulta.query(consultaDaConta));
      } catch (causa) {
        throw causa instanceof FalhaDepoisDaEntrada ? causa : new FalhaDepoisDaEntrada(causa);
      }
    },
    onSuccess: (destino) => {
      if ("rota" in destino) {
        void navegar(destino.rota, { replace: true });
      } else {
        navegarParaPagina(destino.pagina);
      }
    },
    onError: (erro) => {
      if (recusaDo(erro).repetivel) {
        enviando.current = false;
      }
    },
  });

  const recusa = confirmacao.isError ? recusaDo(confirmacao.error) : null;
  // Depois do 200 o botão segue ocupado até a navegação: o token já foi gasto.
  const ocupado = confirmacao.isPending || confirmacao.isSuccess;
  const final = recusa !== null && !recusa.repetivel;

  useEffect(() => {
    if (final) {
      mensagemFinal.current?.focus();
    }
  }, [final]);

  function entrar() {
    if (token === null || enviando.current) {
      return;
    }
    enviando.current = true;
    confirmacao.mutate(token);
  }

  return (
    <>
      <title>{CONFIRMAR.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{CONFIRMAR.titulo}</h1>
      {token === null ? (
        <>
          <p className="mt-(--e-4) mb-0 text-texto-2 text-[1.125rem] leading-[1.5]">{CONFIRMAR.semToken}</p>
          <Link to="/entrar" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
            {CONFIRMAR.pedirNovoLink}
          </Link>
        </>
      ) : final ? (
        <>
          <p
            ref={mensagemFinal}
            tabIndex={-1}
            className="mt-(--e-4) mb-0 text-marfim text-[1.125rem] leading-[1.5] focus:[outline:none]"
          >
            {recusa.texto}
          </p>
          {recusa.saida === "pedirNovoLink" && (
            <Link to="/entrar" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
              {CONFIRMAR.pedirNovoLink}
            </Link>
          )}
          {recusa.saida === "terraco" && (
            // Página inteira: o terraço não é rota da casca.
            <a href="/" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
              {CONFIRMAR.continuarNoTerraco}
            </a>
          )}
        </>
      ) : (
        <>
          <p className="mt-(--e-4) mb-0 text-texto-2 text-[1.125rem] leading-[1.5]">{CONFIRMAR.instrucao}</p>
          <button
            type="button"
            className={`mt-(--e-6) w-full ${BOTAO_PRIMARIO}`}
            aria-disabled={ocupado ? true : undefined}
            aria-describedby={recusa ? "confirmar-erro" : undefined}
            onClick={entrar}
          >
            {ocupado ? CONFIRMAR.entrando : CONFIRMAR.entrar}
          </button>
          <p className="mt-(--e-3) mb-0 text-[0.9375rem] leading-[1.45] text-coral" id="confirmar-erro" role="alert">
            {recusa?.texto ?? ""}
          </p>
        </>
      )}
    </>
  );
}

/** A tela na rota /entrar/confirmar: o token vem do loader, que o toma da casca uma vez só. */
export function ConfirmarEntradaDaRota(): ReactElement {
  const { token } = useLoaderData<DadosDaConfirmacao>();
  return <ConfirmarEntrada token={token} />;
}
