import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, type MouseEvent, type ReactElement } from "react";
import { Link, useLoaderData, useNavigate } from "react-router";
import { excluirConta, sairDaConta } from "../../api/conta";
import type { Conta } from "../../api/estadoDaConta";
import { ErroHttp } from "../../api/http";
import { DialogoDeConfirmacao } from "../../componentes/DialogoDeConfirmacao";
import { BOTAO_PERIGO, BOTAO_SECUNDARIO_SEM_MARGEM, LINK_DE_ACAO, TITULO_DA_CASCA } from "../../componentes/estilos";
import { CAMINHO_DA_VERIFICACAO } from "../../rotas/caminhosDaCasca";
import { consultaDaConta } from "../../rotas/consultas";
import { CONFIRMAR, MINHA_CONTA } from "./textos";

const ID_DA_FALHA_AO_SAIR = "minha-conta-falha-ao-sair";
const ID_DA_EXCLUSAO = "minha-conta-exclusao";
const ID_DO_PERFIL = "minha-conta-perfil";
const ID_DA_FALHA_AO_EXCLUIR = "minha-conta-falha-ao-excluir";

const AVISO_DE_FALHA = "m-0 text-[0.9375rem] leading-[1.45] text-coral";
const PARAGRAFO = "m-0 text-texto-2 text-[1.125rem] leading-[1.5]";

/** A marca do selo: decorativa, o texto ao lado já diz tudo. */
function MarcaDoSelo(): ReactElement {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      className="size-5 shrink-0 text-verde-agua forced-colors:text-[CanvasText]"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="10" cy="10" r="8.5" />
      <path d="m6.5 10.25 2.25 2.25 4.75-5" />
    </svg>
  );
}

/** O texto da recusa: a conta indisponível tem a mensagem do servidor; o resto, a falha sem resposta. */
function textoDaFalha(erro: Error, falhaSemResposta: string): string {
  if (erro instanceof ErroHttp && erro.codigo === "CONTA_INDISPONIVEL") {
    return erro.mensagem ?? CONFIRMAR.contaIndisponivel;
  }
  return falhaSemResposta;
}

function sessaoJaEncerrada(erro: Error): boolean {
  return erro instanceof ErroHttp && erro.codigo === "NAO_AUTENTICADO";
}

/**
 * A conta da sessão (aguardando verificação ou ativa, pelo guarda da rota): sair e excluir. Excluir
 * pede confirmação num diálogo, um par só de botão e diálogo. Depois de sair ou excluir, a conta sai
 * do cache para não deixar estado logado fantasma. Quem aguarda a verificação recebe o link para
 * ela (página inteira: a verificação vive num documento próprio); a conta verificada mostra o selo, e
 * a conta ativa, a entrada para o perfil.
 */
export function MinhaConta(): ReactElement {
  const conta = useLoaderData<Conta>();
  const clienteDeConsulta = useQueryClient();
  const navegar = useNavigate();
  const [dialogoAberto, setDialogoAberto] = useState(false);
  const [excluida, setExcluida] = useState(false);
  const [falhaAoSair, setFalhaAoSair] = useState<string | null>(null);
  const [falhaAoExcluir, setFalhaAoExcluir] = useState<string | null>(null);
  const tituloDaExcluida = useRef<HTMLHeadingElement>(null);
  // Trava síncrona, a mesma para sair e excluir: o isPending só chega no render seguinte, e um duplo
  // toque nessa janela mandaria o pedido duas vezes.
  const enviando = useRef(false);

  useEffect(() => {
    if (excluida) {
      tituloDaExcluida.current?.focus();
    }
  }, [excluida]);

  function esquecerAConta() {
    clienteDeConsulta.removeQueries({ queryKey: consultaDaConta.queryKey, exact: true });
  }

  function irParaEntrar() {
    esquecerAConta();
    void navegar("/entrar", { replace: true });
  }

  const saida = useMutation({
    mutationFn: sairDaConta,
    // A trava segue fechada até a navegação.
    onSuccess: irParaEntrar,
    onError: (erro) => {
      // Sem sessão (expirou, ou saiu em outra aba), o efeito de sair já aconteceu.
      if (sessaoJaEncerrada(erro)) {
        irParaEntrar();
        return;
      }
      enviando.current = false;
      setFalhaAoSair(textoDaFalha(erro, MINHA_CONTA.falhaAoSair));
    },
  });

  const exclusao = useMutation({
    mutationFn: excluirConta,
    // O 204 já expirou o cookie; a trava segue fechada, a tela muda de vez.
    onSuccess: () => {
      esquecerAConta();
      setExcluida(true);
    },
    onError: (erro) => {
      if (sessaoJaEncerrada(erro)) {
        irParaEntrar();
        return;
      }
      enviando.current = false;
      // Fechar devolve o foco ao botão Excluir conta, que aponta para o aviso.
      setDialogoAberto(false);
      setFalhaAoExcluir(textoDaFalha(erro, MINHA_CONTA.falhaAoExcluir));
    },
  });

  function sair() {
    if (enviando.current) {
      return;
    }
    enviando.current = true;
    setFalhaAoSair(null);
    saida.mutate();
  }

  function confirmarExclusao() {
    if (enviando.current) {
      return;
    }
    enviando.current = true;
    setFalhaAoExcluir(null);
    exclusao.mutate();
  }

  if (excluida) {
    return (
      <>
        <title>{MINHA_CONTA.tituloDaAbaExcluida}</title>
        <h1
          ref={tituloDaExcluida}
          tabIndex={-1}
          className={`${TITULO_DA_CASCA} focus:not-focus-visible:[outline:none]`}
        >
          {MINHA_CONTA.tituloExcluida}
        </h1>
        {/* Página inteira: o terraço não é rota da casca. */}
        <a href="/" className={`mt-(--e-6) ${LINK_DE_ACAO}`}>
          {MINHA_CONTA.voltarAoTerraco}
        </a>
      </>
    );
  }

  const saindo = saida.isPending || saida.isSuccess;
  const excluindo = exclusao.isPending;
  // Com o diálogo fechado à força no meio da exclusão, o Sair fica à vista: aparece indisponível.
  const sairIndisponivel = saindo || excluindo;

  function abrirODialogo(evento: MouseEvent<HTMLButtonElement>) {
    if (saindo) {
      return;
    }
    // O Safari e o Firefox do macOS não focam o botão no clique; o diálogo devolve o foco a quem o
    // tinha ao abrir.
    evento.currentTarget.focus();
    setDialogoAberto(true);
  }

  return (
    <>
      <title>{MINHA_CONTA.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{MINHA_CONTA.titulo}</h1>
      {conta.estado === "AGUARDANDO_VERIFICACAO" && (
        <div className="mt-(--e-4) grid gap-(--e-2) justify-items-start">
          <p className={PARAGRAFO}>{MINHA_CONTA.faltaConfirmar}</p>
          <a href={CAMINHO_DA_VERIFICACAO} className={LINK_DE_ACAO}>
            {MINHA_CONTA.confirmarIdade}
          </a>
        </div>
      )}
      {conta.verificada && (
        <div className="mt-(--e-4) flex items-start gap-(--e-3)">
          <MarcaDoSelo />
          <div className="grid gap-(--e-1)">
            <p className="m-0 font-semibold text-[1.125rem] leading-[1.25]">{MINHA_CONTA.selo}</p>
            <p className="m-0 text-texto-2 leading-[1.5]">{MINHA_CONTA.descricaoDoSelo}</p>
          </div>
        </div>
      )}
      {conta.estado === "ATIVA" && (
        <section aria-labelledby={ID_DO_PERFIL} className="mt-(--e-6) grid gap-(--e-1) justify-items-start">
          <h2 id={ID_DO_PERFIL} className="m-0 font-titulo font-semibold text-[1.375rem] leading-[1.2]">
            {MINHA_CONTA.tituloDoPerfil}
          </h2>
          <Link to="/perfil" className={LINK_DE_ACAO}>
            {MINHA_CONTA.meuPerfil}
          </Link>
          <Link to="/perfil/preferencias" className={LINK_DE_ACAO}>
            {MINHA_CONTA.preferencias}
          </Link>
        </section>
      )}
      <div className="mt-(--e-6) grid gap-(--e-2)">
        <button
          type="button"
          className={BOTAO_SECUNDARIO_SEM_MARGEM}
          aria-disabled={sairIndisponivel ? true : undefined}
          aria-describedby={falhaAoSair === null ? undefined : ID_DA_FALHA_AO_SAIR}
          onClick={sair}
        >
          {saindo ? MINHA_CONTA.saindo : MINHA_CONTA.sair}
        </button>
        <p className={AVISO_DE_FALHA} id={ID_DA_FALHA_AO_SAIR} role="alert">
          {falhaAoSair ?? ""}
        </p>
      </div>
      <section
        aria-labelledby={ID_DA_EXCLUSAO}
        className="mt-(--e-8) pt-(--e-6) border-t border-noite-700 grid gap-(--e-3)"
      >
        <h2 id={ID_DA_EXCLUSAO} className="m-0 font-titulo font-semibold text-[1.375rem] leading-[1.2]">
          {MINHA_CONTA.tituloDaExclusao}
        </h2>
        <p className="m-0 text-texto-2 leading-[1.5]">{MINHA_CONTA.avisoDaExclusao}</p>
        <button
          type="button"
          className={`justify-self-start ${BOTAO_PERIGO}`}
          aria-disabled={saindo ? true : undefined}
          aria-describedby={falhaAoExcluir === null ? undefined : ID_DA_FALHA_AO_EXCLUIR}
          onClick={abrirODialogo}
        >
          {MINHA_CONTA.excluir}
        </button>
        <p className={AVISO_DE_FALHA} id={ID_DA_FALHA_AO_EXCLUIR} role="alert">
          {falhaAoExcluir ?? ""}
        </p>
      </section>
      <DialogoDeConfirmacao
        aberto={dialogoAberto}
        titulo={MINHA_CONTA.tituloDoDialogo}
        texto={MINHA_CONTA.textoDoDialogo}
        acao={excluindo ? MINHA_CONTA.excluindo : MINHA_CONTA.excluir}
        aoConfirmar={confirmarExclusao}
        aoCancelar={() => {
          setDialogoAberto(false);
        }}
        enviando={excluindo}
      />
    </>
  );
}
