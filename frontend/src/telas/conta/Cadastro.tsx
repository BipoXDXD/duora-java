import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement, type SubmitEvent } from "react";
import { useNavigate } from "react-router";
import { concluirCadastro, type DadosDoCadastro } from "../../api/conta";
import { ErroHttp } from "../../api/http";
import { obterVersoesLegais } from "../../api/legal";
import {
  BOTAO_PRIMARIO,
  BOTAO_SECUNDARIO_SEM_MARGEM,
  CAMPO,
  DICA,
  ENTRADA_DE_TEXTO,
  LINK_DE_ACAO,
  ROTULO,
  TEXTO_SECUNDARIO,
  TITULO_DA_CASCA,
} from "../../componentes/estilos";
import { consultaDaConta } from "../../rotas/consultas";
import { CADASTRO, CONFIRMAR, textoDeEspera } from "./textos";

const CHAVE_DAS_VERSOES = ["legal", "versoes"] as const;

const ID_DA_DATA = "cadastro-data";
const ID_DA_AJUDA = "cadastro-ajuda-data";
const ID_DO_ACEITE = "cadastro-aceite";
const ID_DO_ERRO = "cadastro-erro";

/** O erro na tela e o campo a que ele pertence (null: erro do envio, sem campo). */
interface Falha {
  texto: string;
  campo: "data" | "aceite" | null;
}

const PARTES_DA_DATA = new Intl.DateTimeFormat("en-CA", {
  timeZone: "America/Sao_Paulo",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** AAAA-MM-DD de hoje no fuso de São Paulo, o mesmo em que o servidor conta a idade. */
function hojeEmSaoPaulo(): string {
  const partes = Object.fromEntries(PARTES_DA_DATA.formatToParts(new Date()).map(({ type, value }) => [type, value]));
  return `${partes.year ?? ""}-${partes.month ?? ""}-${partes.day ?? ""}`;
}

const LINK_EM_TEXTO = "text-ambar underline underline-offset-4 hover:text-ambar-claro";

function LinkDoDocumento({ href, nome }: { href: string; nome: string }): ReactElement {
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" className={LINK_EM_TEXTO}>
      {nome}
      <span className="sr-only"> {CADASTRO.novaAba}</span>
    </a>
  );
}

/**
 * Conclui o cadastro da conta pendente: data de nascimento e aceite das versões atuais dos Termos e da
 * Política, lidas do servidor. O botão fica sempre disponível e explica o que falta. Menor de idade
 * recebe a recusa no lugar do formulário: a conta já foi excluída e a sessão, encerrada, então a
 * conta sai do cache.
 */
export function Cadastro(): ReactElement {
  const clienteDeConsulta = useQueryClient();
  const navegar = useNavigate();
  // A versão que a pessoa aceita é a que a tela mostrou: sem releitura ao voltar da aba dos Termos.
  // Só o TERMOS_DESATUALIZADOS pede a versão nova, e junto desmarca a caixa.
  const versoes = useQuery({
    queryKey: CHAVE_DAS_VERSOES,
    queryFn: obterVersoesLegais,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });
  const campoDaData = useRef<HTMLInputElement>(null);
  const caixaDoAceite = useRef<HTMLInputElement>(null);
  const mensagemFinal = useRef<HTMLParagraphElement>(null);
  const [falha, setFalha] = useState<Falha | null>(null);
  /** Desfecho sem volta (menor de idade, conta indisponível): o formulário sai. */
  const [final, setFinal] = useState<string | null>(null);
  // Trava síncrona: o isPending só chega no render seguinte, e um duplo toque nessa janela mandaria
  // o cadastro duas vezes (o segundo recebe 409).
  const enviando = useRef(false);

  useEffect(() => {
    if (final !== null) {
      mensagemFinal.current?.focus();
    }
  }, [final]);

  function mostrar(nova: Falha) {
    setFalha(nova);
    if (nova.campo === "data") {
      campoDaData.current?.focus();
    } else if (nova.campo === "aceite") {
      caixaDoAceite.current?.focus();
    }
  }

  /** Pelo código do contrato (C7); o que não tem código conhecido é falha sem resposta conclusiva. */
  function tratarRecusa(erro: Error) {
    if (!(erro instanceof ErroHttp)) {
      mostrar({ texto: CADASTRO.falha, campo: null });
      return;
    }
    switch (erro.codigo) {
      case "MENOR_DE_IDADE":
        clienteDeConsulta.removeQueries({ queryKey: consultaDaConta.queryKey });
        setFinal(CADASTRO.menorDeIdade);
        return;
      case "CONTA_INDISPONIVEL":
        setFinal(erro.mensagem ?? CONFIRMAR.contaIndisponivel);
        return;
      case "NAO_AUTENTICADO":
        clienteDeConsulta.removeQueries({ queryKey: consultaDaConta.queryKey });
        void navegar("/entrar", { replace: true });
        return;
      case "CADASTRO_JA_CONCLUIDO":
        // O guarda de /aguardando leva à tela certa se a conta já estiver ativa.
        void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaConta.queryKey, exact: true });
        void navegar("/aguardando", { replace: true });
        return;
      case "DATA_INVALIDA":
        mostrar({ texto: erro.mensagem ?? CADASTRO.dataInvalida, campo: "data" });
        return;
      case "ACEITE_NECESSARIO":
        mostrar({ texto: erro.mensagem ?? CADASTRO.semAceite, campo: "aceite" });
        return;
      case "TERMOS_DESATUALIZADOS":
        // A versão nova chega sem recarregar; o aceite antigo não vale para ela.
        if (caixaDoAceite.current) {
          caixaDoAceite.current.checked = false;
        }
        void clienteDeConsulta.invalidateQueries({ queryKey: CHAVE_DAS_VERSOES });
        mostrar({ texto: erro.mensagem ?? CADASTRO.termosDesatualizados, campo: "aceite" });
        return;
      case "LIMITE_DE_TAXA":
        mostrar({ texto: textoDeEspera(erro.tentarDeNovoEmSegundos ?? 0), campo: null });
        return;
    }
    mostrar({ texto: CADASTRO.falha, campo: null });
  }

  const cadastro = useMutation({
    mutationFn: concluirCadastro,
    // Sem pausa fora do ar: a falha de rede vira o aviso, e o botão volta a valer.
    networkMode: "always",
    onSuccess: async () => {
      // A trava segue fechada até a navegação: o cadastro já foi aceito.
      await clienteDeConsulta.invalidateQueries({ queryKey: consultaDaConta.queryKey, exact: true });
      await navegar("/aguardando", { replace: true });
    },
    onError: (erro) => {
      enviando.current = false;
      tratarRecusa(erro);
    },
  });

  function concluir(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (enviando.current || !versoes.data) {
      return;
    }
    const dataDeNascimento = campoDaData.current?.value ?? "";
    if (dataDeNascimento === "") {
      mostrar({ texto: CADASTRO.semData, campo: "data" });
      return;
    }
    if (!caixaDoAceite.current?.checked) {
      mostrar({ texto: CADASTRO.semAceite, campo: "aceite" });
      return;
    }
    enviando.current = true;
    setFalha(null);
    const dados: DadosDoCadastro = {
      dataDeNascimento,
      versaoTermos: versoes.data.termos.versao,
      versaoPrivacidade: versoes.data.privacidade.versao,
      aceito: true,
    };
    cadastro.mutate(dados);
  }

  function descricao(campo: Falha["campo"], ...base: string[]): string | undefined {
    const ids = falha?.campo === campo ? [...base, ID_DO_ERRO] : base;
    return ids.length > 0 ? ids.join(" ") : undefined;
  }

  const ocupado = cadastro.isPending || cadastro.isSuccess;

  return (
    <>
      <title>{CADASTRO.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{CADASTRO.titulo}</h1>
      {final !== null ? (
        <>
          <p
            ref={mensagemFinal}
            tabIndex={-1}
            className="mt-(--e-4) mb-0 text-marfim text-[1.125rem] leading-[1.5] focus:[outline:none]"
          >
            {final}
          </p>
          {/* Página inteira: o terraço não é rota da casca. */}
          <a href="/" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
            {CADASTRO.voltarAoTerraco}
          </a>
        </>
      ) : versoes.data === undefined && versoes.isError ? (
        // Só sem dados: uma releitura que falha não tira o formulário nem o que foi digitado.
        <div className="mt-(--e-6) grid gap-(--e-3)">
          <p className={TEXTO_SECUNDARIO}>{CADASTRO.falhaDosTermos}</p>
          <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={() => void versoes.refetch()}>
            {CADASTRO.tentarDeNovo}
          </button>
        </div>
      ) : versoes.data === undefined ? (
        <p className={`mt-(--e-6) ${TEXTO_SECUNDARIO}`} role="status">
          {CADASTRO.carregandoTermos}
        </p>
      ) : (
        <form className="mt-(--e-6) grid gap-(--e-5)" noValidate onSubmit={concluir}>
          <div className={CAMPO}>
            <label className={ROTULO} htmlFor={ID_DA_DATA}>
              {CADASTRO.rotuloDaData}
            </label>
            <input
              ref={campoDaData}
              className={ENTRADA_DE_TEXTO}
              id={ID_DA_DATA}
              name="dataDeNascimento"
              type="date"
              max={hojeEmSaoPaulo()}
              autoComplete="bday"
              aria-describedby={descricao("data", ID_DA_AJUDA)}
              aria-invalid={falha?.campo === "data" ? true : undefined}
            />
            <p className={DICA} id={ID_DA_AJUDA}>
              {CADASTRO.ajudaDaData}
            </p>
          </div>
          <div className="grid gap-(--e-3)">
            <p className="m-0 text-texto-2 leading-[1.5]">
              {CADASTRO.leiaAntes} <LinkDoDocumento href="/termos" nome={CADASTRO.termos} /> {CADASTRO.e}{" "}
              <LinkDoDocumento href="/privacidade" nome={CADASTRO.privacidade} />.
            </p>
            <div className="flex items-start gap-(--e-3)">
              <input
                ref={caixaDoAceite}
                className="mt-[2px] size-6 flex-none accent-ambar cursor-pointer"
                id={ID_DO_ACEITE}
                name="aceito"
                type="checkbox"
                aria-describedby={descricao("aceite")}
                aria-invalid={falha?.campo === "aceite" ? true : undefined}
              />
              <label className="leading-[1.45] cursor-pointer" htmlFor={ID_DO_ACEITE}>
                {CADASTRO.rotuloDoAceite}
              </label>
            </div>
          </div>
          <button
            type="submit"
            className={`w-full ${BOTAO_PRIMARIO}`}
            aria-disabled={ocupado ? true : undefined}
            aria-describedby={falha?.campo === null ? ID_DO_ERRO : undefined}
          >
            {ocupado ? CADASTRO.concluindo : CADASTRO.concluir}
          </button>
          <p className="m-0 text-[0.9375rem] leading-[1.45] text-coral" id={ID_DO_ERRO} role="alert">
            {falha?.texto ?? ""}
          </p>
        </form>
      )}
    </>
  );
}
