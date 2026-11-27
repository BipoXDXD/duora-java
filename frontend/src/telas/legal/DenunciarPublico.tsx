import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement, type SubmitEvent } from "react";
import { enviarDenuncia, type DenunciaPublica, type MotivoDaDenuncia } from "../../api/denuncia";
import { ErroHttp } from "../../api/http";
import {
  BOTAO_PRIMARIO,
  CAMPO,
  DICA,
  ENTRADA_DE_TEXTO,
  ROTULO,
  TEXTO_SECUNDARIO,
  TITULO_DA_CASCA,
} from "../../componentes/estilos";
import type { ApiTurnstile } from "../../componentes/Turnstile";
import { CaixaDaVerificacao } from "../conta/CaixaDaVerificacao";
import { textoDeEspera, VERIFICACAO } from "../conta/textos";
import { useVerificacaoHumana } from "../conta/useVerificacaoHumana";
import { aparar } from "./aparar";
import { DENUNCIAR, textoDoContador } from "./textos";

/** Os limites do servidor (C12), em caracteres (code points), depois do `strip` (ver `aparar`). */
const DESCRICAO_MINIMA = 10;
const DESCRICAO_MAXIMA = 2000;
const REFERENCIA_MAXIMA = 200;
/** O Postgres não guarda o caractere nulo em text; o servidor recusa. */
const NULO = "\u0000";

const ID_DA_DESCRICAO = "denunciar-descricao";
const ID_DA_DICA_DA_DESCRICAO = "denunciar-dica-descricao";
const ID_DO_CONTADOR = "denunciar-contador";
const ID_DA_REFERENCIA = "denunciar-referencia";
const ID_DA_DICA_DA_REFERENCIA = "denunciar-dica-referencia";
const ID_DO_EMAIL = "denunciar-email";
const ID_DO_MOTIVO_DO_BOTAO = "denunciar-motivo-do-botao";
const ID_DA_FALHA_DA_VERIFICACAO = "denunciar-falha-verificacao";
const ID_DO_ERRO = "denunciar-erro";
/** A pausa entre limpar e reescrever o anúncio, para o leitor de tela repetir a mesma recusa. */
const PAUSA_DO_ANUNCIO_MS = 100;

type Campo = "motivo" | "descricao" | "referencia" | "email";

function idDoErroDo(campo: Campo): string {
  return `denunciar-erro-${campo}`;
}

/** A borda mais grossa marca o campo recusado sem depender só da cor. */
const ENTRADA = `${ENTRADA_DE_TEXTO} aria-invalid:border-2`;

/** O erro na tela e o campo a que ele pertence (null: erro do envio, sem campo). */
interface Falha {
  texto: string;
  campo: Campo | null;
}

/** O 422 do servidor diz o campo só pela mensagem do contrato, a mesma que a tela usa. */
const CAMPO_DA_RECUSA: Partial<Record<string, Campo>> = {
  [DENUNCIAR.semMotivo]: "motivo",
  [DENUNCIAR.descricaoForaDoTamanho]: "descricao",
  [DENUNCIAR.referenciaLonga]: "referencia",
  [DENUNCIAR.emailInvalido]: "email",
};

/**
 * Conta code points, como o servidor: um emoji é um caractere, não duas unidades de UTF-16 (o
 * `length`). Não são grafemas: um emoji composto conta mais de um, lá e aqui.
 */
function caracteres(texto: string): number {
  return Array.from(texto).length;
}

function cabe(texto: string, minimo: number, maximo: number): boolean {
  const total = caracteres(texto);
  return !texto.includes(NULO) && total >= minimo && total <= maximo;
}

function ehMotivo(valor: FormDataEntryValue | null): valor is MotivoDaDenuncia {
  return DENUNCIAR.motivos.some(([motivo]) => motivo === valor);
}

function texto(valor: FormDataEntryValue | null): string {
  return typeof valor === "string" ? aparar(valor) : "";
}

function falhaDoEnvio(erro: Error): Falha {
  if (erro instanceof ErroHttp) {
    if (erro.status === 429) {
      return { texto: textoDeEspera(erro.tentarDeNovoEmSegundos ?? 0), campo: null };
    }
    if (erro.status === 422 && erro.mensagem) {
      return { texto: erro.mensagem, campo: CAMPO_DA_RECUSA[erro.mensagem] ?? null };
    }
  }
  return { texto: DENUNCIAR.falha, campo: null };
}

/** O erro sob o campo: texto, ícone e barra à esquerda, para não depender só da cor. */
function ErroDoCampo({ campo, texto }: { campo: Campo; texto: string }): ReactElement {
  return (
    <p
      className="m-0 flex items-start gap-(--e-2) border-l-4 border-coral pl-(--e-2) text-[0.9375rem] leading-[1.45] font-semibold text-coral"
      id={idDoErroDo(campo)}
    >
      <svg aria-hidden="true" className="mt-[0.15em] size-4 flex-none" viewBox="0 0 16 16" fill="currentColor">
        <path
          fillRule="evenodd"
          d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm-.75 3.5h1.5v5h-1.5v-5Zm0 6.5h1.5v1.5h-1.5V11Z"
        />
      </svg>
      {texto}
    </p>
  );
}

interface PropsDeDenunciarPublico {
  carregarTurnstile?: () => Promise<ApiTurnstile>;
}

/**
 * Denúncia sem conta. O envio só fica disponível com um token do Turnstile; a tela confere os campos
 * antes, com as mesmas regras e mensagens do servidor, mostra o erro sob o campo e leva o foco a ele.
 * O alerta geral fica para o que não é de um campo (limite, verificação humana, falha do envio). Toda
 * resposta gasta o token: o widget reinicia e pede outro. No sucesso, o formulário volta ao início e o
 * foco vai para a mensagem do servidor. Nada do que a pessoa escreve vai para o console.
 */
export function DenunciarPublico({ carregarTurnstile: carregar }: PropsDeDenunciarPublico): ReactElement {
  const verificacao = useVerificacaoHumana();
  const formulario = useRef<HTMLFormElement>(null);
  const primeiroMotivo = useRef<HTMLInputElement>(null);
  const campoDaDescricao = useRef<HTMLTextAreaElement>(null);
  const campoDaReferencia = useRef<HTMLInputElement>(null);
  const campoDoEmail = useRef<HTMLInputElement>(null);
  const mensagemDeSucesso = useRef<HTMLParagraphElement>(null);
  const [descricao, setDescricao] = useState("");
  const [falha, setFalha] = useState<Falha | null>(null);
  const [anuncio, setAnuncio] = useState("");

  /** Toda troca de falha limpa o anúncio; o efeito abaixo o reescreve, se a falha for de um campo. */
  function trocarFalha(nova: Falha | null) {
    setAnuncio("");
    setFalha(nova);
  }

  // O foco vai depois do commit: o campo já tem o aria-invalid e o erro na descrição quando o leitor
  // de tela o anuncia. Cada falha é um objeto novo, então a mesma recusa repetida volta a focar. Se o
  // campo já tinha o foco (Enter no e-mail), o focus() não anuncia nada: a região de status repete o
  // erro, reescrita depois de uma pausa para valer também na recusa repetida.
  useEffect(() => {
    if (falha?.campo == null) {
      return;
    }
    const texto = falha.texto;
    const reescrita = window.setTimeout(() => {
      setAnuncio(texto);
    }, PAUSA_DO_ANUNCIO_MS);
    const campos: Record<Campo, { current: HTMLElement | null }> = {
      motivo: primeiroMotivo,
      descricao: campoDaDescricao,
      referencia: campoDaReferencia,
      email: campoDoEmail,
    };
    campos[falha.campo].current?.focus();
    return () => {
      window.clearTimeout(reescrita);
    };
  }, [falha]);

  const denuncia = useMutation({
    mutationFn: enviarDenuncia,
    onSuccess: () => {
      formulario.current?.reset();
      setDescricao("");
    },
    onError: (erro) => {
      trocarFalha(falhaDoEnvio(erro));
    },
    onSettled: verificacao.liberar,
  });

  useEffect(() => {
    if (denuncia.isSuccess) {
      mensagemDeSucesso.current?.focus();
    }
  }, [denuncia.isSuccess]);

  const { token, falhou: verificacaoFalhou } = verificacao;
  const indisponivel = token === null || denuncia.isPending;
  const descricaoLimpa = aparar(descricao);
  const caracteresDaDescricao = caracteres(descricaoLimpa);

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    // A tela só confere os campos com o envio livre; a trava fecha depois da conferência.
    if (!verificacao.livre()) {
      return;
    }
    const dados = new FormData(evento.currentTarget);
    const motivo = dados.get("motivo");
    const referencia = texto(dados.get("referencia"));
    const emailContato = texto(dados.get("emailContato"));
    if (!ehMotivo(motivo)) {
      trocarFalha({ texto: DENUNCIAR.semMotivo, campo: "motivo" });
      return;
    }
    if (!cabe(descricaoLimpa, DESCRICAO_MINIMA, DESCRICAO_MAXIMA)) {
      trocarFalha({ texto: DENUNCIAR.descricaoForaDoTamanho, campo: "descricao" });
      return;
    }
    if (referencia !== "" && !cabe(referencia, 1, REFERENCIA_MAXIMA)) {
      trocarFalha({ texto: DENUNCIAR.referenciaLonga, campo: "referencia" });
      return;
    }
    const tokenDoEnvio = verificacao.tomar();
    if (tokenDoEnvio === null) {
      return;
    }
    trocarFalha(null);
    const nova: DenunciaPublica = { motivo, descricao: descricaoLimpa, tokenVerificacao: tokenDoEnvio };
    if (referencia !== "") {
      nova.referencia = referencia;
    }
    if (emailContato !== "") {
      nova.emailContato = emailContato;
    }
    denuncia.mutate(nova);
  }

  const erroDoCampo = falha?.campo == null ? null : { campo: falha.campo, texto: falha.texto };
  const erroGeral = falha?.campo === null ? falha.texto : "";

  function descricaoDo(campo: Campo, ...base: string[]): string | undefined {
    const ids = erroDoCampo?.campo === campo ? [...base, idDoErroDo(campo)] : base;
    return ids.length > 0 ? ids.join(" ") : undefined;
  }

  function invalido(campo: Campo): true | undefined {
    return erroDoCampo?.campo === campo ? true : undefined;
  }

  function erroSob(campo: Campo): ReactElement | null {
    return erroDoCampo?.campo === campo ? <ErroDoCampo campo={campo} texto={erroDoCampo.texto} /> : null;
  }

  return (
    <>
      <title>{DENUNCIAR.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{DENUNCIAR.titulo}</h1>
      <p className={`mt-(--e-4) ${TEXTO_SECUNDARIO} text-[1.0625rem] leading-[1.5]`}>{DENUNCIAR.introducao}</p>
      <form ref={formulario} className="mt-(--e-6) grid gap-(--e-5)" noValidate onSubmit={enviar}>
        <fieldset
          // A barra fica sempre, transparente: no erro só a cor muda, sem deslocar o layout.
          className={`m-0 p-0 min-w-0 border-0 border-l-4 pl-(--e-3) ${invalido("motivo") ? "border-coral" : "border-transparent"}`}
          aria-describedby={descricaoDo("motivo")}
        >
          <legend className={`${ROTULO} p-0 mb-(--e-2)`}>{DENUNCIAR.rotuloDoMotivo}</legend>
          {DENUNCIAR.motivos.map(([valor, rotulo], indice) => (
            <label key={valor} className="flex items-center gap-(--e-3) min-h-[44px] leading-[1.45] cursor-pointer">
              <input
                ref={indice === 0 ? primeiroMotivo : undefined}
                className="size-5 flex-none accent-ambar cursor-pointer"
                type="radio"
                name="motivo"
                value={valor}
                aria-invalid={invalido("motivo")}
                aria-describedby={descricaoDo("motivo")}
              />
              {rotulo}
            </label>
          ))}
          {erroSob("motivo")}
        </fieldset>
        <div className={CAMPO}>
          <label className={ROTULO} htmlFor={ID_DA_DESCRICAO}>
            {DENUNCIAR.rotuloDaDescricao}
          </label>
          {/* Sem maxLength: ele conta unidades de UTF-16, e o limite do servidor é em caracteres. */}
          <textarea
            ref={campoDaDescricao}
            className={`${ENTRADA} resize-y`}
            id={ID_DA_DESCRICAO}
            name="descricao"
            rows={6}
            value={descricao}
            onChange={(evento) => {
              setDescricao(evento.target.value);
            }}
            aria-describedby={descricaoDo("descricao", ID_DA_DICA_DA_DESCRICAO, ID_DO_CONTADOR)}
            aria-invalid={invalido("descricao")}
          />
          <div className="flex items-baseline justify-between gap-(--e-3)">
            <p className={DICA} id={ID_DA_DICA_DA_DESCRICAO}>
              {DENUNCIAR.dicaDaDescricao}
            </p>
            <p
              className={`m-0 text-[0.875rem] tabular-nums ${caracteresDaDescricao > DESCRICAO_MAXIMA ? "text-coral" : "text-texto-3"}`}
              id={ID_DO_CONTADOR}
            >
              {textoDoContador(caracteresDaDescricao, DESCRICAO_MAXIMA)}
            </p>
          </div>
          {erroSob("descricao")}
        </div>
        <div className={CAMPO}>
          <label className={ROTULO} htmlFor={ID_DA_REFERENCIA}>
            {DENUNCIAR.rotuloDaReferencia}
          </label>
          <input
            ref={campoDaReferencia}
            className={ENTRADA}
            id={ID_DA_REFERENCIA}
            name="referencia"
            type="text"
            autoComplete="off"
            spellCheck={false}
            aria-describedby={descricaoDo("referencia", ID_DA_DICA_DA_REFERENCIA)}
            aria-invalid={invalido("referencia")}
          />
          <p className={DICA} id={ID_DA_DICA_DA_REFERENCIA}>
            {DENUNCIAR.dicaDaReferencia}
          </p>
          {erroSob("referencia")}
        </div>
        <div className={CAMPO}>
          <label className={ROTULO} htmlFor={ID_DO_EMAIL}>
            {DENUNCIAR.rotuloDoEmail}
          </label>
          <input
            ref={campoDoEmail}
            className={ENTRADA}
            id={ID_DO_EMAIL}
            name="emailContato"
            type="email"
            autoComplete="email"
            spellCheck={false}
            aria-describedby={descricaoDo("email")}
            aria-invalid={invalido("email")}
          />
          {erroSob("email")}
        </div>
        <CaixaDaVerificacao verificacao={verificacao} idDaFalha={ID_DA_FALHA_DA_VERIFICACAO} carregar={carregar} />
        <div className="grid gap-(--e-2)">
          <button
            type="submit"
            className={BOTAO_PRIMARIO}
            aria-disabled={indisponivel ? true : undefined}
            aria-describedby={
              token === null
                ? verificacaoFalhou
                  ? ID_DA_FALHA_DA_VERIFICACAO
                  : ID_DO_MOTIVO_DO_BOTAO
                : erroGeral !== ""
                  ? ID_DO_ERRO
                  : undefined
            }
          >
            {denuncia.isPending ? DENUNCIAR.enviando : DENUNCIAR.enviar}
          </button>
          {token === null && !verificacaoFalhou && (
            // Com a mensagem de sucesso na tela, a dica sai da vista, mas segue como motivo do botão.
            <p className={denuncia.isSuccess ? "sr-only" : DICA} id={ID_DO_MOTIVO_DO_BOTAO}>
              {VERIFICACAO.aguarde}
            </p>
          )}
        </div>
      </form>
      <p
        ref={mensagemDeSucesso}
        tabIndex={denuncia.isSuccess ? -1 : undefined}
        className="mt-(--e-5) mb-0 text-[1.0625rem] leading-[1.5] text-marfim focus:[outline:none]"
        role="status"
      >
        {denuncia.isSuccess ? denuncia.data.mensagem : ""}
      </p>
      <p className="mt-(--e-2) mb-0 text-[0.9375rem] leading-[1.45] text-coral" id={ID_DO_ERRO} role="alert">
        {erroGeral}
      </p>
      <p className="sr-only" role="status">
        {anuncio}
      </p>
    </>
  );
}
