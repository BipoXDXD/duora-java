import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement, type SubmitEvent } from "react";
import { ErroHttp } from "../../api/http";
import {
  apagarOrientacao,
  salvarOrientacao,
  type DadosDeOrientacao,
  type EdicaoDaOrientacao,
  type Orientacao as OpcaoDeOrientacao,
  ORIENTACOES,
} from "../../api/perfil";
import {
  BOTAO_PRIMARIO,
  BOTAO_SECUNDARIO_SEM_MARGEM,
  CAMPO,
  DICA,
  ENTRADA_DE_TEXTO,
  ERRO_DE_CAMPO,
  ROTULO,
  seDisponivel,
  TITULO_DA_CASCA,
} from "../../componentes/estilos";
import { consultaDaOrientacao } from "../../rotas/consultas";
import { semEspacosNasPontas, textoCurtoCabe } from "./textoDigitado";
import { useTravaDeEnvio } from "./useTravaDeEnvio";
import { camposComErro } from "./camposComErro";
import { ERROS_DA_ORIENTACAO, ORIENTACAO, ROTULO_DA_ORIENTACAO } from "./textos";

/** O formulário como a tela o guarda: sem orientação até a escolha, e a descrição como digitada. */
interface EdicaoNaTela {
  orientacao: OpcaoDeOrientacao | null;
  descricaoOrientacao: string;
  mostrarOrientacao: boolean;
}

type Campo = "orientacao" | "descricaoOrientacao";
type Erros = Partial<Record<Campo, string>>;

const DESCRICAO_MAXIMA = 40;

const ID = {
  descricao: "orientacao-descricao",
  dicaDaDescricao: "orientacao-dica-descricao",
  concordouEm: "orientacao-concordou-em",
  semConsentimento: "orientacao-sem-consentimento",
  erroAoSalvar: "orientacao-erro",
  erroAoApagar: "orientacao-erro-apagar",
  erro: (campo: Campo) => `orientacao-erro-${campo}`,
} as const;

const GRUPO = "m-0 p-0 min-w-0 border-0 border-l-4 pl-(--e-3)";
const LEGENDA = `${ROTULO} p-0 mb-(--e-2)`;
const OPCAO = "flex items-center gap-(--e-3) min-h-[44px] leading-[1.45] cursor-pointer";
const MARCA = "size-5 flex-none accent-ambar cursor-pointer";
const STATUS = "m-0 text-[1.0625rem] leading-[1.5] text-marfim empty:hidden";
const ALERTA = "m-0 text-[0.9375rem] leading-[1.45] text-coral empty:hidden focus:[outline:none]";

const DATA_DO_CONSENTIMENTO = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "long",
  timeZone: "America/Sao_Paulo",
});

/** O formulário a partir dos dados gravados; sem dados, tudo vazio. */
function deDados(d: DadosDeOrientacao | null): EdicaoNaTela {
  return d === null
    ? { orientacao: null, descricaoOrientacao: "", mostrarOrientacao: false }
    : {
        orientacao: d.orientacao,
        descricaoOrientacao: d.descricaoOrientacao ?? "",
        mostrarOrientacao: d.mostrarOrientacao,
      };
}

/** Os erros da edição, iguais às regras do servidor (D4): a descrição contada por code point, sem espaços nas pontas. */
function validar(e: EdicaoNaTela): Erros {
  if (e.orientacao === null) {
    return { orientacao: ERROS_DA_ORIENTACAO.semOrientacao };
  }
  if (e.orientacao === "OUTRA") {
    if (!textoCurtoCabe(e.descricaoOrientacao, DESCRICAO_MAXIMA)) {
      return { descricaoOrientacao: ERROS_DA_ORIENTACAO.descricao };
    }
  }
  return {};
}

/** O corpo do PUT: a descrição vai só com Prefiro descrever, e o consentimento sempre verdadeiro. */
function paraEnvio(e: EdicaoNaTela & { orientacao: OpcaoDeOrientacao }): EdicaoDaOrientacao {
  return {
    orientacao: e.orientacao,
    descricaoOrientacao: e.orientacao === "OUTRA" ? semEspacosNasPontas(e.descricaoOrientacao) : null,
    mostrarOrientacao: e.mostrarOrientacao,
    consentimento: true,
  };
}

/**
 * Orientação: opcional, salva só com o consentimento marcado, e apagada sem confirmação (é só
 * preencher de novo para voltar). Nunca entra no pareamento; só aparece às conexões com "mostrar".
 */
export function Orientacao(): ReactElement {
  const clienteDeConsulta = useQueryClient();
  // O loader da rota já leu a orientação: a tela não pede de novo ao montar.
  const { data: dados } = useSuspenseQuery({ ...consultaDaOrientacao, refetchOnMount: false });
  const [edicao, setEdicao] = useState<EdicaoNaTela>(() => deDados(dados));
  // Sem dados gravados, a caixa nunca vem marcada: o consentimento é um gesto de quem preenche.
  const [consentimento, setConsentimento] = useState(dados !== null);
  const [erros, setErros] = useState<Erros>({});
  const [erroAoSalvar, setErroAoSalvar] = useState("");
  const [erroAoApagar, setErroAoApagar] = useState("");
  const [situacao, setSituacao] = useState<"salva" | "apagada" | null>(null);
  const trava = useTravaDeEnvio();
  const primeiraOpcao = useRef<HTMLInputElement>(null);
  const campoDaDescricao = useRef<HTMLInputElement>(null);
  const alertaAoSalvar = useRef<HTMLParagraphElement>(null);
  const alertaAoApagar = useRef<HTMLParagraphElement>(null);
  const status = useRef<HTMLParagraphElement>(null);

  // Para onde o foco vai depois do próximo render (o campo da descrição só existe com Prefiro
  // descrever; o botão de apagar some ao apagar). Quem marca também muda o estado, e o render vem.
  const focarEm = useRef<Campo | "alertaAoSalvar" | "alertaAoApagar" | "status" | null>(null);
  useEffect(() => {
    if (focarEm.current === null) {
      return;
    }
    const alvo = {
      orientacao: primeiraOpcao.current,
      descricaoOrientacao: campoDaDescricao.current,
      alertaAoSalvar: alertaAoSalvar.current,
      alertaAoApagar: alertaAoApagar.current,
      status: status.current,
    }[focarEm.current];
    focarEm.current = null;
    alvo?.focus();
  });

  const salvar = useMutation({
    mutationFn: salvarOrientacao,
    onSuccess: (novos) => {
      clienteDeConsulta.setQueryData(consultaDaOrientacao.queryKey, novos);
      setEdicao(deDados(novos));
      setSituacao("salva");
    },
    onError: (erro) => {
      setErroAoSalvar(
        erro instanceof ErroHttp && erro.status === 422 ? (erro.mensagem ?? ORIENTACAO.falha) : ORIENTACAO.falha,
      );
      focarEm.current = "alertaAoSalvar";
    },
    onSettled: () => {
      trava.abrir();
    },
  });

  const apagar = useMutation({
    mutationFn: apagarOrientacao,
    onSuccess: () => {
      // Zerar antes de reler: a tela não mostra, nem por um instante, os dados apagados.
      clienteDeConsulta.setQueryData(consultaDaOrientacao.queryKey, null);
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaOrientacao.queryKey, exact: true });
      setEdicao(deDados(null));
      setConsentimento(false);
      setErros({});
      setErroAoSalvar("");
      setSituacao("apagada");
      focarEm.current = "status";
    },
    onError: () => {
      setErroAoApagar(ORIENTACAO.falhaAoApagar);
      focarEm.current = "alertaAoApagar";
    },
  });

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (trava.fechada() || !consentimento) {
      return;
    }
    setSituacao(null);
    setErroAoSalvar("");
    setErroAoApagar("");
    const recusas = validar(edicao);
    if (Object.keys(recusas).length > 0 || edicao.orientacao === null) {
      setErros(recusas);
      focarEm.current = recusas.orientacao === undefined ? "descricaoOrientacao" : "orientacao";
      return;
    }
    setErros({});
    trava.fechar();
    salvar.mutate(paraEnvio({ ...edicao, orientacao: edicao.orientacao }));
  }

  /** Editar de novo tira a situação anterior: ela valeria para o que já mudou. */
  function mudar(parte: Partial<EdicaoNaTela>) {
    setSituacao(null);
    setEdicao((atual) => ({ ...atual, ...parte }));
  }

  /** Trocar de opção tira os erros: valiam para a escolha anterior. */
  function escolher(orientacao: OpcaoDeOrientacao) {
    mudar({ orientacao });
    setErros({});
  }

  const { descricao, erroDo } = camposComErro(erros, ID.erro, `${ERRO_DE_CAMPO} mt-(--e-2)`);

  const prefereDescrever = edicao.orientacao === "OUTRA";
  const temErro = erros.orientacao !== undefined || erros.descricaoOrientacao !== undefined;
  const salvarIndisponivel = !consentimento || salvar.isPending;
  // Só o que vale para salvar: a falha de apagar fica no botão de apagar.
  const descricaoDoSalvar = [
    ...(consentimento ? [] : [ID.semConsentimento]),
    ...(erroAoSalvar === "" ? [] : [ID.erroAoSalvar]),
  ].join(" ");

  return (
    <>
      <title>{ORIENTACAO.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{ORIENTACAO.titulo}</h1>
      <p className="mt-(--e-4) mb-0 px-(--e-4) py-(--e-3) border-l-4 border-ambar rounded-(--raio) bg-noite-950 text-marfim leading-[1.5] max-w-[60ch]">
        {ORIENTACAO.aviso}
      </p>
      <form className="mt-(--e-6) grid gap-(--e-6)" noValidate onSubmit={enviar}>
        <fieldset
          className={`${GRUPO} ${temErro ? "border-coral" : "border-transparent"}`}
          // O erro da descrição fica só no campo dela; no grupo, seria lido duas vezes.
          aria-describedby={descricao("orientacao")}
        >
          <legend className={LEGENDA}>{ORIENTACAO.rotulo}</legend>
          {ORIENTACOES.map((opcao, indice) => (
            <label key={opcao} className={OPCAO}>
              <input
                ref={indice === 0 ? primeiraOpcao : undefined}
                className={MARCA}
                type="radio"
                name="orientacao"
                value={opcao}
                checked={edicao.orientacao === opcao}
                onChange={() => {
                  escolher(opcao);
                }}
                aria-invalid={erros.orientacao === undefined ? undefined : true}
              />
              {ROTULO_DA_ORIENTACAO[opcao]}
            </label>
          ))}
          {erroDo("orientacao")}
          {prefereDescrever && (
            <div className={`${CAMPO} mt-(--e-2)`}>
              <label className={ROTULO} htmlFor={ID.descricao}>
                {ORIENTACAO.rotuloDaDescricao}
              </label>
              {/* Sem maxLength: ele conta unidades de UTF-16, e o limite do servidor é em caracteres. */}
              <input
                ref={campoDaDescricao}
                className={ENTRADA_DE_TEXTO}
                id={ID.descricao}
                name="descricaoOrientacao"
                type="text"
                value={edicao.descricaoOrientacao}
                onChange={(evento) => {
                  mudar({ descricaoOrientacao: evento.target.value });
                }}
                aria-describedby={descricao("descricaoOrientacao", ID.dicaDaDescricao)}
                aria-invalid={erros.descricaoOrientacao === undefined ? undefined : true}
              />
              <p className={DICA} id={ID.dicaDaDescricao}>
                {ORIENTACAO.dicaDaDescricao}
              </p>
              {erroDo("descricaoOrientacao")}
            </div>
          )}
        </fieldset>

        <label className={OPCAO}>
          <input
            className={MARCA}
            type="checkbox"
            name="mostrarOrientacao"
            checked={edicao.mostrarOrientacao}
            onChange={(evento) => {
              mudar({ mostrarOrientacao: evento.target.checked });
            }}
          />
          {ORIENTACAO.mostrar}
        </label>

        <div className="grid gap-(--e-2)">
          <label className={`${OPCAO} font-semibold`}>
            <input
              className={MARCA}
              type="checkbox"
              name="consentimento"
              checked={consentimento}
              onChange={(evento) => {
                setSituacao(null);
                setConsentimento(evento.target.checked);
              }}
              aria-describedby={dados === null ? undefined : ID.concordouEm}
            />
            {ORIENTACAO.consentimento}
          </label>
          {dados !== null && (
            <p className={`${DICA} pl-[calc(1.25rem+var(--e-3))]`} id={ID.concordouEm}>
              {ORIENTACAO.concordouEm(DATA_DO_CONSENTIMENTO.format(new Date(dados.consentidaEm)))}
            </p>
          )}
        </div>

        <div className="grid gap-(--e-3)">
          <button
            type="submit"
            className={`w-full ${BOTAO_PRIMARIO}`}
            aria-disabled={salvarIndisponivel ? true : undefined}
            aria-describedby={descricaoDoSalvar === "" ? undefined : descricaoDoSalvar}
          >
            {salvar.isPending ? ORIENTACAO.salvando : ORIENTACAO.salvar}
          </button>
          {!consentimento && (
            <p className={DICA} id={ID.semConsentimento}>
              {ORIENTACAO.semConsentimento}
            </p>
          )}
          {/* Sempre na árvore: o leitor de tela anuncia quando o texto aparece. */}
          <p ref={status} tabIndex={-1} className={`${STATUS} focus:not-focus-visible:[outline:none]`} role="status">
            {situacao === "salva" ? ORIENTACAO.salva : situacao === "apagada" ? ORIENTACAO.apagada : ""}
          </p>
          <p ref={alertaAoSalvar} tabIndex={-1} className={ALERTA} id={ID.erroAoSalvar} role="alert">
            {erroAoSalvar}
          </p>
        </div>
      </form>

      {/* Sem dados gravados, não há o que apagar. */}
      {dados !== null && (
        <div className="mt-(--e-8) pt-(--e-6) border-t border-noite-700 grid gap-(--e-3)">
          <button
            type="button"
            className={`${BOTAO_SECUNDARIO_SEM_MARGEM} text-marfim`}
            aria-disabled={apagar.isPending ? true : undefined}
            aria-describedby={erroAoApagar === "" ? undefined : ID.erroAoApagar}
            onClick={seDisponivel(apagar.isPending, () => {
              setSituacao(null);
              setErroAoApagar("");
              apagar.mutate();
            })}
          >
            {apagar.isPending ? ORIENTACAO.apagando : ORIENTACAO.apagar}
          </button>
          <p ref={alertaAoApagar} tabIndex={-1} className={ALERTA} id={ID.erroAoApagar} role="alert">
            {erroAoApagar}
          </p>
        </div>
      )}
    </>
  );
}
