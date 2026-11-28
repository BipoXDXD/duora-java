import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement, type SubmitEvent } from "react";
import { ErroHttp } from "../../api/http";
import {
  salvarPerfil,
  versaoObsoleta,
  type EdicaoDoPerfil,
  type Intencao,
  type MeuPerfil as Perfil,
  type PerfilPublico,
  type Pronomes,
} from "../../api/perfil";
import { CartaoDoPerfil } from "../../componentes/CartaoDoPerfil";
import {
  BOTAO_PRIMARIO,
  CAMPO,
  DICA,
  ENTRADA_DE_TEXTO,
  ROTULO,
  TEXTO_SECUNDARIO,
  TITULO_DA_CASCA,
  TITULO_DE_SECAO,
} from "../../componentes/estilos";
import { consultaDaConta, consultaDoMeuPerfil, consultaDoNamoro } from "../../rotas/consultas";
import { contarCaracteres } from "./contar";
import { EnviarFoto } from "./EnviarFoto";
import { BIO_MAXIMA, bioNormalizada, deMeuPerfil, tiraNamoro, validar, type ErrosDoPerfil } from "./formularioDoPerfil";
import { FaixaDaPausa, PausaDaConta } from "./PausaDaConta";
import { MEU_PERFIL, ROTULO_DAS_INTENCOES, ROTULO_DOS_PRONOMES } from "./textos";

type Campo = keyof ErrosDoPerfil;

/** A ordem do formulário: o foco vai ao primeiro campo com erro. */
const ORDEM_DOS_CAMPOS: readonly Campo[] = ["apelido", "descricaoPronomes", "intencoes", "bio"];

const PRONOMES = Object.keys(ROTULO_DOS_PRONOMES) as Pronomes[];
const INTENCOES = Object.keys(ROTULO_DAS_INTENCOES) as Intencao[];

/** O código de cada recusa do servidor e o campo em que a mensagem aparece. */
const CAMPO_DO_CODIGO: Readonly<Record<string, Campo>> = {
  APELIDO_INVALIDO: "apelido",
  IDENTIDADE_INVALIDA: "descricaoPronomes",
  INTENCAO_INVALIDA: "intencoes",
  BIO_INVALIDA: "bio",
};

const ID = {
  apelido: "perfil-apelido",
  dicaDoApelido: "perfil-dica-apelido",
  descricaoPronomes: "perfil-descricao-pronomes",
  dicaDaDescricao: "perfil-dica-descricao-pronomes",
  avisoDeNamoro: "perfil-aviso-namoro",
  bio: "perfil-bio",
  dicaDaBio: "perfil-dica-bio",
  contador: "perfil-contador-bio",
  erroGeral: "perfil-erro",
  cartao: "perfil-titulo-cartao",
  erro: (campo: Campo) => `perfil-erro-${campo}`,
} as const;

const GRUPO = "m-0 p-0 min-w-0 border-0 border-l-4 pl-(--e-3)";
const OPCAO = "flex items-center gap-(--e-3) min-h-[44px] leading-[1.45] cursor-pointer";
const MARCA = "size-5 flex-none accent-ambar cursor-pointer";
const ERRO = "m-0 text-[0.875rem] leading-[1.45] text-coral";

/** O que a busca mostra de si: a foto borrada da aprovada, os pronomes pelo rótulo e o selo. */
function comoOsOutrosVeem(perfil: Perfil, apelido: string, verificado: boolean): PerfilPublico {
  const pronomes =
    perfil.pronomes === null
      ? null
      : perfil.pronomes === "OUTROS"
        ? perfil.descricaoPronomes
        : ROTULO_DOS_PRONOMES[perfil.pronomes];
  return { apelido, foto: perfil.foto.aprovada?.borrada ?? null, pronomes, verificado };
}

/** Só os campos que valem: a descrição dos pronomes vai só com Prefiro descrever. */
function paraEnvio(e: EdicaoDoPerfil): EdicaoDoPerfil {
  return { ...e, descricaoPronomes: e.pronomes === "OUTROS" ? e.descricaoPronomes : null };
}

/**
 * Meu perfil: apelido, pronomes, intenções e bio, salvos com a versão lida (o 409 traz a versão
 * nova e troca o formulário), a foto, o cartão com o que os outros veem e a pausa da conta. A
 * validação roda no envio, com o foco no primeiro campo recusado.
 */
export function MeuPerfil(): ReactElement {
  const clienteDeConsulta = useQueryClient();
  // O loader da rota já leu as duas: a tela não pede de novo ao montar.
  const { data: perfil } = useSuspenseQuery({ ...consultaDoMeuPerfil, refetchOnMount: false });
  const { data: conta } = useSuspenseQuery({ ...consultaDaConta, refetchOnMount: false });
  const [edicao, setEdicao] = useState<EdicaoDoPerfil>(() => deMeuPerfil(perfil));
  const [erros, setErros] = useState<ErrosDoPerfil>({});
  const [erroGeral, setErroGeral] = useState("");
  const [salvo, setSalvo] = useState(false);
  // Trava síncrona: o isPending só chega no render seguinte.
  const enviando = useRef(false);
  const campoDoApelido = useRef<HTMLInputElement>(null);
  const campoDaDescricao = useRef<HTMLInputElement>(null);
  const primeiraIntencao = useRef<HTMLInputElement>(null);
  const campoDaBio = useRef<HTMLTextAreaElement>(null);
  const pronomeMarcado = useRef<HTMLInputElement>(null);
  const alerta = useRef<HTMLParagraphElement>(null);
  const faixaDaPausa = useRef<HTMLParagraphElement>(null);

  // Para onde o foco vai depois do próximo render (o campo da descrição dos pronomes só existe com
  // Prefiro descrever). Quem marca também muda o estado, e o render vem.
  const focarEm = useRef<Campo | "alerta" | null>(null);
  useEffect(() => {
    if (focarEm.current === null) {
      return;
    }
    const alvo = {
      apelido: campoDoApelido.current,
      descricaoPronomes: campoDaDescricao.current ?? pronomeMarcado.current,
      intencoes: primeiraIntencao.current,
      bio: campoDaBio.current,
      alerta: alerta.current,
    }[focarEm.current];
    focarEm.current = null;
    alvo?.focus();
  });

  function mostrarErros(novos: ErrosDoPerfil) {
    setErros(novos);
    focarEm.current = ORDEM_DOS_CAMPOS.find((campo) => novos[campo] !== undefined) ?? null;
  }

  async function recarregarAposConflito() {
    try {
      const novo = await clienteDeConsulta.query(consultaDoMeuPerfil);
      setEdicao(deMeuPerfil(novo));
      setErroGeral(MEU_PERFIL.versaoObsoleta);
    } catch {
      setErroGeral(MEU_PERFIL.falha);
    }
    focarEm.current = "alerta";
  }

  const salvar = useMutation({
    mutationFn: salvarPerfil,
    onSuccess: (novo) => {
      clienteDeConsulta.setQueryData(consultaDoMeuPerfil.queryKey, novo);
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDoNamoro.queryKey, exact: true });
      setEdicao(deMeuPerfil(novo));
      setSalvo(true);
    },
    onError: async (erro) => {
      if (versaoObsoleta(erro)) {
        await recarregarAposConflito();
        return;
      }
      if (erro instanceof ErroHttp && erro.status === 422) {
        const campo = CAMPO_DO_CODIGO[erro.codigo];
        if (campo !== undefined) {
          mostrarErros({ [campo]: erro.mensagem ?? MEU_PERFIL.falha });
          return;
        }
      }
      setErroGeral(MEU_PERFIL.falha);
      focarEm.current = "alerta";
    },
    onSettled: () => {
      enviando.current = false;
    },
  });

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (enviando.current) {
      return;
    }
    setSalvo(false);
    setErroGeral("");
    const recusas = validar(edicao);
    if (Object.keys(recusas).length > 0) {
      mostrarErros(recusas);
      return;
    }
    setErros({});
    enviando.current = true;
    salvar.mutate(paraEnvio(edicao));
  }

  /** Editar de novo tira o "Perfil salvo.": ele valeria para o que já mudou. */
  function mudar(parte: Partial<EdicaoDoPerfil>) {
    setSalvo(false);
    setEdicao((atual) => ({ ...atual, ...parte }));
  }

  /** Trocar de pronomes tira o erro da descrição: ele valia para a escolha anterior. */
  function escolherPronomes(pronomes: Pronomes | null) {
    mudar({ pronomes });
    setErros((atuais) => ({ ...atuais, descricaoPronomes: undefined }));
  }

  function alternarIntencao(intencao: Intencao, marcada: boolean) {
    // Na ordem do contrato, qualquer que seja a ordem dos cliques.
    const escolhidas = new Set(edicao.intencoes);
    if (marcada) {
      escolhidas.add(intencao);
    } else {
      escolhidas.delete(intencao);
    }
    mudar({ intencoes: INTENCOES.filter((i) => escolhidas.has(i)) });
  }

  /** As descrições de um campo: as fixas e, se houver, o erro dele. */
  function descricao(campo: Campo, ...fixas: string[]): string | undefined {
    const ids = erros[campo] === undefined ? fixas : [...fixas, ID.erro(campo)];
    return ids.length > 0 ? ids.join(" ") : undefined;
  }

  function erroDo(campo: Campo): ReactElement | null {
    const texto = erros[campo];
    return texto === undefined ? null : (
      <p className={ERRO} id={ID.erro(campo)}>
        {texto}
      </p>
    );
  }

  const invalido = (campo: Campo) => (erros[campo] === undefined ? undefined : true);
  const caracteresDaBio = contarCaracteres(bioNormalizada(edicao.bio));
  const avisarNamoro = tiraNamoro(perfil, edicao);
  const prefereDescrever = edicao.pronomes === "OUTROS";

  return (
    <>
      <title>{MEU_PERFIL.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{MEU_PERFIL.titulo}</h1>
      <FaixaDaPausa ref={faixaDaPausa} pausada={perfil.pausada} />
      {perfil.versao === null && (
        <p className={`mt-(--e-4) ${TEXTO_SECUNDARIO} text-[1.0625rem] leading-[1.5]`}>{MEU_PERFIL.introducao}</p>
      )}
      <form className="mt-(--e-6) grid gap-(--e-6)" noValidate onSubmit={enviar}>
        <div className={CAMPO}>
          <label className={ROTULO} htmlFor={ID.apelido}>
            {MEU_PERFIL.rotuloDoApelido}
          </label>
          {/* Sem maxLength: ele conta unidades de UTF-16, e o limite do servidor é em caracteres. */}
          <input
            ref={campoDoApelido}
            className={ENTRADA_DE_TEXTO}
            id={ID.apelido}
            name="apelido"
            type="text"
            autoComplete="nickname"
            value={edicao.apelido}
            onChange={(evento) => {
              mudar({ apelido: evento.target.value });
            }}
            aria-describedby={descricao("apelido", ID.dicaDoApelido)}
            aria-invalid={invalido("apelido")}
          />
          <p className={DICA} id={ID.dicaDoApelido}>
            {MEU_PERFIL.dicaDoApelido}
          </p>
          {erroDo("apelido")}
        </div>

        <fieldset
          className={`${GRUPO} ${erros.descricaoPronomes === undefined ? "border-transparent" : "border-coral"}`}
          // Aberto, o erro é descrito pelo campo da descrição; no grupo, seria lido duas vezes.
          aria-describedby={prefereDescrever ? undefined : descricao("descricaoPronomes")}
        >
          <legend className={`${ROTULO} p-0 mb-(--e-2)`}>{MEU_PERFIL.rotuloDosPronomes}</legend>
          {[null, ...PRONOMES].map((valor) => {
            const marcado = edicao.pronomes === valor;
            return (
              <label key={valor ?? "nenhum"} className={OPCAO}>
                <input
                  ref={marcado ? pronomeMarcado : undefined}
                  className={MARCA}
                  type="radio"
                  name="pronomes"
                  value={valor ?? ""}
                  checked={marcado}
                  onChange={() => {
                    escolherPronomes(valor);
                  }}
                />
                {valor === null ? MEU_PERFIL.naoMostrar : ROTULO_DOS_PRONOMES[valor]}
              </label>
            );
          })}
          {prefereDescrever && (
            <div className={`${CAMPO} mt-(--e-2)`}>
              <label className={ROTULO} htmlFor={ID.descricaoPronomes}>
                {MEU_PERFIL.rotuloDaDescricaoDosPronomes}
              </label>
              <input
                ref={campoDaDescricao}
                className={ENTRADA_DE_TEXTO}
                id={ID.descricaoPronomes}
                name="descricaoPronomes"
                type="text"
                value={edicao.descricaoPronomes ?? ""}
                onChange={(evento) => {
                  mudar({ descricaoPronomes: evento.target.value });
                }}
                aria-describedby={descricao("descricaoPronomes", ID.dicaDaDescricao)}
                aria-invalid={invalido("descricaoPronomes")}
              />
              <p className={DICA} id={ID.dicaDaDescricao}>
                {MEU_PERFIL.dicaDaDescricaoDosPronomes}
              </p>
              {erroDo("descricaoPronomes")}
            </div>
          )}
          {!prefereDescrever && erroDo("descricaoPronomes")}
        </fieldset>

        <fieldset
          className={`${GRUPO} ${erros.intencoes === undefined ? "border-transparent" : "border-coral"}`}
          aria-describedby={descricao("intencoes", ...(avisarNamoro ? [ID.avisoDeNamoro] : []))}
        >
          <legend className={`${ROTULO} p-0 mb-(--e-2)`}>{MEU_PERFIL.rotuloDasIntencoes}</legend>
          {INTENCOES.map((intencao, indice) => (
            <label key={intencao} className={OPCAO}>
              <input
                ref={indice === 0 ? primeiraIntencao : undefined}
                className={MARCA}
                type="checkbox"
                name="intencoes"
                value={intencao}
                checked={edicao.intencoes.includes(intencao)}
                onChange={(evento) => {
                  alternarIntencao(intencao, evento.target.checked);
                }}
                aria-invalid={invalido("intencoes")}
              />
              {ROTULO_DAS_INTENCOES[intencao]}
            </label>
          ))}
          {erroDo("intencoes")}
          {/* Sempre na árvore: o leitor de tela anuncia o aviso quando ele aparece. */}
          <p
            className="m-0 text-[0.9375rem] leading-[1.45] text-ambar empty:hidden"
            id={ID.avisoDeNamoro}
            role="status"
          >
            {avisarNamoro ? MEU_PERFIL.avisoDeTirarNamoro : ""}
          </p>
        </fieldset>

        <div className={CAMPO}>
          <label className={ROTULO} htmlFor={ID.bio}>
            {MEU_PERFIL.rotuloDaBio}
          </label>
          <textarea
            ref={campoDaBio}
            className={`${ENTRADA_DE_TEXTO} resize-y`}
            id={ID.bio}
            name="bio"
            rows={5}
            value={edicao.bio}
            onChange={(evento) => {
              mudar({ bio: evento.target.value });
            }}
            aria-describedby={descricao("bio", ID.dicaDaBio, ID.contador)}
            aria-invalid={invalido("bio")}
          />
          <div className="flex items-baseline justify-between gap-(--e-3)">
            <p className={DICA} id={ID.dicaDaBio}>
              {MEU_PERFIL.dicaDaBio}
            </p>
            <p
              className={`m-0 text-[0.875rem] tabular-nums ${caracteresDaBio > BIO_MAXIMA ? "text-coral" : "text-texto-3"}`}
              id={ID.contador}
            >
              {MEU_PERFIL.contador(caracteresDaBio, BIO_MAXIMA)}
            </p>
          </div>
          {erroDo("bio")}
        </div>

        <div className="grid gap-(--e-3)">
          <button
            type="submit"
            className={`w-full ${BOTAO_PRIMARIO}`}
            aria-disabled={salvar.isPending ? true : undefined}
            aria-describedby={erroGeral === "" ? undefined : ID.erroGeral}
          >
            {salvar.isPending ? MEU_PERFIL.salvando : MEU_PERFIL.salvar}
          </button>
          <p className="m-0 text-[1.0625rem] leading-[1.5] text-marfim empty:hidden" role="status">
            {salvo ? MEU_PERFIL.salvo : ""}
          </p>
          <p
            ref={alerta}
            tabIndex={-1}
            className="m-0 text-[0.9375rem] leading-[1.45] text-coral empty:hidden focus:[outline:none]"
            id={ID.erroGeral}
            role="alert"
          >
            {erroGeral}
          </p>
        </div>
      </form>

      <EnviarFoto apelido={perfil.apelido} foto={perfil.foto} />

      <section aria-labelledby={ID.cartao} className="mt-(--e-8) pt-(--e-6) border-t border-noite-700 grid gap-(--e-4)">
        <h2 id={ID.cartao} className={TITULO_DE_SECAO}>
          {MEU_PERFIL.tituloDoCartao}
        </h2>
        <p className={`${TEXTO_SECUNDARIO} leading-[1.5]`}>{MEU_PERFIL.explicacaoDoCartao}</p>
        {/* Sem perfil salvo, não há o que mostrar além da explicação. */}
        {perfil.apelido !== null && (
          <CartaoDoPerfil
            perfil={comoOsOutrosVeem(perfil, perfil.apelido, conta?.verificada ?? false)}
            carregamento="lazy"
          />
        )}
      </section>

      {/* Sem perfil salvo, o servidor recusa a pausa. */}
      {perfil.versao !== null && <PausaDaConta pausada={perfil.pausada} faixa={faixaDaPausa} />}
    </>
  );
}
