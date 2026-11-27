import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement, type SubmitEvent } from "react";
import { obterConfiguracao } from "../../api/conta";
import { enviarDenuncia, type DenunciaPublica, type MotivoDaDenuncia } from "../../api/denuncia";
import { ErroHttp } from "../../api/http";
import {
  BOTAO_PRIMARIO,
  BOTAO_SECUNDARIO_SEM_MARGEM,
  CAMPO,
  DICA,
  ENTRADA_DE_TEXTO,
  ROTULO,
  TEXTO_SECUNDARIO,
  TITULO_DA_CASCA,
} from "../../componentes/estilos";
import { carregarTurnstile, Turnstile, type ApiTurnstile, type ControleDoTurnstile } from "../../componentes/Turnstile";
import { textoDeEspera, VERIFICACAO } from "../conta/textos";
import { DENUNCIAR, textoDoContador } from "./textos";

/** Os limites do servidor (C12), em caracteres (code points), depois de tirar os espaços das pontas. */
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

type Campo = "motivo" | "descricao" | "referencia" | "email";

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
  return typeof valor === "string" ? valor.trim() : "";
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

interface PropsDeDenunciarPublico {
  carregarTurnstile?: () => Promise<ApiTurnstile>;
}

/**
 * Denúncia sem conta. O envio só fica disponível com um token do Turnstile; a tela confere os campos
 * antes, com as mesmas regras e mensagens do servidor, e leva o foco ao primeiro campo recusado. Toda
 * resposta gasta o token: o widget reinicia e pede outro. No sucesso, o formulário volta ao início e o
 * foco vai para a mensagem do servidor. Nada do que a pessoa escreve vai para o console.
 */
export function DenunciarPublico({
  carregarTurnstile: carregar = carregarTurnstile,
}: PropsDeDenunciarPublico): ReactElement {
  const configuracao = useQuery({
    queryKey: ["conta", "configuracao"],
    queryFn: obterConfiguracao,
    staleTime: Infinity,
  });
  const turnstile = useRef<ControleDoTurnstile>(null);
  const formulario = useRef<HTMLFormElement>(null);
  const primeiroMotivo = useRef<HTMLInputElement>(null);
  const campoDaDescricao = useRef<HTMLTextAreaElement>(null);
  const campoDaReferencia = useRef<HTMLInputElement>(null);
  const campoDoEmail = useRef<HTMLInputElement>(null);
  const mensagemDeSucesso = useRef<HTMLParagraphElement>(null);
  const [descricao, setDescricao] = useState("");
  const [falha, setFalha] = useState<Falha | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [widgetFalhou, setWidgetFalhou] = useState(false);
  // Trava síncrona: o isPending só chega no render seguinte, e um duplo toque nessa janela mandaria
  // outra denúncia com o mesmo token (já gasto).
  const enviando = useRef(false);

  const campos: Record<Campo, { current: HTMLElement | null }> = {
    motivo: primeiroMotivo,
    descricao: campoDaDescricao,
    referencia: campoDaReferencia,
    email: campoDoEmail,
  };

  function mostrar(nova: Falha) {
    setFalha(nova);
    if (nova.campo !== null) {
      campos[nova.campo].current?.focus();
    }
  }

  const denuncia = useMutation({
    mutationFn: enviarDenuncia,
    // Sem pausa fora do ar: a falha de rede vira o aviso, e o botão volta a valer com o token novo.
    networkMode: "always",
    onSuccess: () => {
      formulario.current?.reset();
      setDescricao("");
    },
    onError: (erro) => {
      mostrar(falhaDoEnvio(erro));
    },
    onSettled: () => {
      enviando.current = false;
      setToken(null);
      turnstile.current?.reiniciar();
    },
  });

  useEffect(() => {
    if (denuncia.isSuccess) {
      mensagemDeSucesso.current?.focus();
    }
  }, [denuncia.isSuccess]);

  const verificacaoFalhou = widgetFalhou || configuracao.isError;
  const indisponivel = token === null || denuncia.isPending;
  const descricaoLimpa = descricao.trim();
  const caracteresDaDescricao = caracteres(descricaoLimpa);

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (token === null || enviando.current) {
      return;
    }
    const dados = new FormData(evento.currentTarget);
    const motivo = dados.get("motivo");
    const referencia = texto(dados.get("referencia"));
    const emailContato = texto(dados.get("emailContato"));
    if (!ehMotivo(motivo)) {
      mostrar({ texto: DENUNCIAR.semMotivo, campo: "motivo" });
      return;
    }
    if (!cabe(descricaoLimpa, DESCRICAO_MINIMA, DESCRICAO_MAXIMA)) {
      mostrar({ texto: DENUNCIAR.descricaoForaDoTamanho, campo: "descricao" });
      return;
    }
    if (referencia !== "" && !cabe(referencia, 1, REFERENCIA_MAXIMA)) {
      mostrar({ texto: DENUNCIAR.referenciaLonga, campo: "referencia" });
      return;
    }
    enviando.current = true;
    setFalha(null);
    const nova: DenunciaPublica = { motivo, descricao: descricaoLimpa, tokenVerificacao: token };
    if (referencia !== "") {
      nova.referencia = referencia;
    }
    if (emailContato !== "") {
      nova.emailContato = emailContato;
    }
    denuncia.mutate(nova);
  }

  /** Sair do aviso monta um widget novo; o carregador tenta o script de novo se ele não veio. */
  function tentarDeNovo() {
    setWidgetFalhou(false);
    if (configuracao.isError) {
      void configuracao.refetch();
    }
  }

  function descricaoDo(campo: Campo, ...base: string[]): string | undefined {
    const ids = falha?.campo === campo ? [...base, ID_DO_ERRO] : base;
    return ids.length > 0 ? ids.join(" ") : undefined;
  }

  function invalido(campo: Campo): true | undefined {
    return falha?.campo === campo ? true : undefined;
  }

  return (
    <>
      <title>{DENUNCIAR.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{DENUNCIAR.titulo}</h1>
      <p className={`mt-(--e-4) ${TEXTO_SECUNDARIO} text-[1.0625rem] leading-[1.5]`}>{DENUNCIAR.introducao}</p>
      <form ref={formulario} className="mt-(--e-6) grid gap-(--e-5)" noValidate onSubmit={enviar}>
        <fieldset className="m-0 p-0 border-0 min-w-0" aria-describedby={descricaoDo("motivo")}>
          <legend className={`${ROTULO} p-0 mb-(--e-2)`}>{DENUNCIAR.rotuloDoMotivo}</legend>
          {DENUNCIAR.motivos.map(([valor, rotulo], indice) => (
            <label key={valor} className="flex items-center gap-(--e-3) min-h-[44px] leading-[1.45] cursor-pointer">
              <input
                ref={indice === 0 ? primeiroMotivo : undefined}
                className="size-5 flex-none accent-ambar cursor-pointer"
                type="radio"
                name="motivo"
                value={valor}
              />
              {rotulo}
            </label>
          ))}
        </fieldset>
        <div className={CAMPO}>
          <label className={ROTULO} htmlFor={ID_DA_DESCRICAO}>
            {DENUNCIAR.rotuloDaDescricao}
          </label>
          {/* Sem maxLength: ele conta unidades de UTF-16, e o limite do servidor é em caracteres. */}
          <textarea
            ref={campoDaDescricao}
            className={`${ENTRADA_DE_TEXTO} resize-y`}
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
        </div>
        <div className={CAMPO}>
          <label className={ROTULO} htmlFor={ID_DA_REFERENCIA}>
            {DENUNCIAR.rotuloDaReferencia}
          </label>
          <input
            ref={campoDaReferencia}
            className={ENTRADA_DE_TEXTO}
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
        </div>
        <div className={CAMPO}>
          <label className={ROTULO} htmlFor={ID_DO_EMAIL}>
            {DENUNCIAR.rotuloDoEmail}
          </label>
          <input
            ref={campoDoEmail}
            className={ENTRADA_DE_TEXTO}
            id={ID_DO_EMAIL}
            name="emailContato"
            type="email"
            autoComplete="email"
            spellCheck={false}
            aria-describedby={descricaoDo("email")}
            aria-invalid={invalido("email")}
          />
        </div>
        <div className="min-h-[65px]">
          {verificacaoFalhou ? (
            <div className="grid gap-(--e-2)">
              <p className={TEXTO_SECUNDARIO} id={ID_DA_FALHA_DA_VERIFICACAO}>
                {VERIFICACAO.falha}
              </p>
              <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={tentarDeNovo}>
                {VERIFICACAO.tentarDeNovo}
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
                : falha?.campo === null
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
        {falha?.texto ?? ""}
      </p>
    </>
  );
}
