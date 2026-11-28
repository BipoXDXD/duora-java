import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement, type SubmitEvent } from "react";
import { Link } from "react-router";
import { ErroHttp } from "../../api/http";
import {
  apagarNamoro,
  salvarNamoro,
  type Genero,
  GENEROS,
} from "../../api/perfil";
import { DialogoDeConfirmacao } from "../../componentes/DialogoDeConfirmacao";
import {
  BOTAO_PRIMARIO,
  BOTAO_SECUNDARIO_SEM_MARGEM,
  CAMPO,
  DICA,
  ENTRADA_DE_TEXTO,
  ERRO_DE_CAMPO,
  LINK_DE_ACAO,
  ROTULO,
  seDisponivel,
  TEXTO_SECUNDARIO,
  TITULO_DA_CASCA,
} from "../../componentes/estilos";
import { consultaDoMeuPerfil, consultaDoNamoro } from "../../rotas/consultas";
import {
  deDadosDeNamoro,
  paraEnvio,
  validarNamoro,
  type EdicaoDoNamoroNaTela,
  type ErrosDoNamoro,
} from "./formularioDoNamoro";
import { NAMORO, ROTULO_DO_GENERO, ROTULO_DO_INTERESSE } from "./textos";
import { useTravaDeEnvio } from "./useTravaDeEnvio";
import { camposComErro } from "./camposComErro";

type Campo = keyof ErrosDoNamoro;

/** A ordem do formulário: o foco vai ao primeiro campo com erro. */
const ORDEM_DOS_CAMPOS: readonly Campo[] = ["genero", "descricaoGenero", "interesses"];


const ID = {
  dicaDoGenero: "namoro-dica-genero",
  descricaoGenero: "namoro-descricao-genero",
  dicaDaDescricao: "namoro-dica-descricao",
  concordouEm: "namoro-concordou-em",
  semConsentimento: "namoro-sem-consentimento",
  erroGeral: "namoro-erro",
  erro: (campo: Campo) => `namoro-erro-${campo}`,
} as const;

const GRUPO = "m-0 p-0 min-w-0 border-0 border-l-4 pl-(--e-3)";
const LEGENDA = `${ROTULO} p-0 mb-(--e-2)`;
const OPCAO = "flex items-center gap-(--e-3) min-h-[44px] leading-[1.45] cursor-pointer";
const MARCA = "size-5 flex-none accent-ambar cursor-pointer";
const STATUS = "m-0 text-[1.0625rem] leading-[1.5] text-marfim empty:hidden";

const DATA_DO_CONSENTIMENTO = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "long",
  timeZone: "America/Sao_Paulo",
});

/**
 * Para namoro: com Namoro nas intenções, o gênero e quem conhecer, salvos só com o consentimento
 * marcado, e o apagamento com confirmação; sem Namoro, só o caminho para Meu perfil. O aviso de
 * apagados fica fora do formulário: ao apagar, Namoro sai das intenções e o formulário some.
 */
export function Namoro(): ReactElement {
  // O loader da rota já leu o perfil: a tela não pede de novo ao montar.
  const { data: perfil } = useSuspenseQuery({ ...consultaDoMeuPerfil, refetchOnMount: false });
  const [apagados, setApagados] = useState(false);
  const statusDeApagados = useRef<HTMLParagraphElement>(null);
  const focarNoStatus = useRef(false);

  useEffect(() => {
    if (focarNoStatus.current) {
      focarNoStatus.current = false;
      statusDeApagados.current?.focus();
    }
  });

  return (
    <>
      <title>{NAMORO.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{NAMORO.titulo}</h1>
      {/* Sempre na árvore: o leitor de tela anuncia quando o texto aparece. */}
      <p
        ref={statusDeApagados}
        tabIndex={-1}
        role="status"
        className={`mt-(--e-4) ${STATUS} focus:not-focus-visible:[outline:none]`}
      >
        {apagados ? NAMORO.apagados : ""}
      </p>
      {perfil.intencoes.includes("NAMORO") ? (
        <FormularioDoNamoro
          aoApagar={() => {
            setApagados(true);
            focarNoStatus.current = true;
          }}
        />
      ) : (
        <div className="mt-(--e-4) grid gap-(--e-3) justify-items-start">
          <p className={`${TEXTO_SECUNDARIO} text-[1.0625rem] leading-[1.5] max-w-[60ch]`}>{NAMORO.desligado}</p>
          <Link to="/perfil" className={LINK_DE_ACAO}>
            {NAMORO.irParaMeuPerfil}
          </Link>
        </div>
      )}
    </>
  );
}

function FormularioDoNamoro({ aoApagar }: { aoApagar: () => void }): ReactElement {
  const clienteDeConsulta = useQueryClient();
  // O loader leu os dados junto com o perfil (sem Namoro, não leu, e este formulário não monta).
  const { data: dados } = useSuspenseQuery({ ...consultaDoNamoro, refetchOnMount: false });
  const [edicao, setEdicao] = useState<EdicaoDoNamoroNaTela>(() => deDadosDeNamoro(dados));
  // Sem dados gravados, a caixa nunca vem marcada: o consentimento é um gesto de quem preenche.
  const [consentimento, setConsentimento] = useState(dados !== null);
  const [erros, setErros] = useState<ErrosDoNamoro>({});
  const [erroGeral, setErroGeral] = useState("");
  // "salvos" mostra o aviso de salvos; "confirmando" abre o diálogo de apagar.
  const [situacao, setSituacao] = useState<"editando" | "salvos" | "confirmando">("editando");
  const trava = useTravaDeEnvio();
  const primeiroGenero = useRef<HTMLInputElement>(null);
  const campoDaDescricao = useRef<HTMLInputElement>(null);
  const primeiroInteresse = useRef<HTMLInputElement>(null);
  const alerta = useRef<HTMLParagraphElement>(null);

  // Para onde o foco vai depois do próximo render (o campo da descrição só existe com Prefiro
  // descrever). Quem marca também muda o estado, e o render vem.
  const focarEm = useRef<Campo | "alerta" | null>(null);
  useEffect(() => {
    if (focarEm.current === null) {
      return;
    }
    const alvo = {
      genero: primeiroGenero.current,
      descricaoGenero: campoDaDescricao.current,
      interesses: primeiroInteresse.current,
      alerta: alerta.current,
    }[focarEm.current];
    focarEm.current = null;
    alvo?.focus();
  });

  function mostrarErros(novos: ErrosDoNamoro) {
    setErros(novos);
    focarEm.current = ORDEM_DOS_CAMPOS.find((campo) => novos[campo] !== undefined) ?? null;
  }

  function mostrarFalha(texto: string) {
    setErroGeral(texto);
    focarEm.current = "alerta";
  }

  const salvar = useMutation({
    mutationFn: salvarNamoro,
    onSuccess: (novos) => {
      clienteDeConsulta.setQueryData(consultaDoNamoro.queryKey, novos);
      // A pendência de dados de namoro do perfil pode ter mudado.
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDoMeuPerfil.queryKey, exact: true });
      setEdicao(deDadosDeNamoro(novos));
      setSituacao("salvos");
    },
    onError: (erro) => {
      if (erro instanceof ErroHttp && erro.status === 422) {
        if (erro.codigo === "INTENCAO_SEM_NAMORO") {
          // Namoro saiu das intenções em outro aparelho: o perfil relido mostra o desligado.
          void clienteDeConsulta.invalidateQueries({ queryKey: consultaDoMeuPerfil.queryKey, exact: true });
        }
        mostrarFalha(erro.mensagem ?? NAMORO.falha);
        return;
      }
      mostrarFalha(NAMORO.falha);
    },
    onSettled: () => {
      trava.abrir();
    },
  });

  const apagar = useMutation({
    mutationFn: apagarNamoro,
    onSuccess: () => {
      setSituacao("editando");
      setEdicao(deDadosDeNamoro(null));
      setConsentimento(false);
      aoApagar();
      // Por prefixo: o perfil (intenções, versão, pendências) e os dados de namoro mudaram.
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDoMeuPerfil.queryKey });
    },
    onError: () => {
      setSituacao("editando");
      mostrarFalha(NAMORO.falhaAoApagar);
    },
  });

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (trava.fechada() || !consentimento) {
      return;
    }
    setSituacao("editando");
    setErroGeral("");
    const recusas = validarNamoro(edicao);
    if (Object.keys(recusas).length > 0 || edicao.genero === null) {
      mostrarErros(recusas);
      return;
    }
    setErros({});
    trava.fechar();
    salvar.mutate(paraEnvio({ ...edicao, genero: edicao.genero }));
  }

  /** Editar de novo tira o "salvos": ele valeria para o que já mudou. */
  function mudar(parte: Partial<EdicaoDoNamoroNaTela>) {
    setSituacao("editando");
    setEdicao((atual) => ({ ...atual, ...parte }));
  }

  /** Trocar de gênero tira os erros do gênero e da descrição: valiam para a escolha anterior. */
  function escolherGenero(genero: Genero) {
    mudar({ genero });
    setErros((atuais) => ({ ...atuais, genero: undefined, descricaoGenero: undefined }));
  }

  function alternarInteresse(genero: Genero, marcado: boolean) {
    // Na ordem do contrato, qualquer que seja a ordem dos cliques.
    const escolhidos = new Set(edicao.interesses);
    if (marcado) {
      escolhidos.add(genero);
    } else {
      escolhidos.delete(genero);
    }
    mudar({ interesses: GENEROS.filter((g) => escolhidos.has(g)) });
  }

  const { descricao, erroDo, invalido } = camposComErro(erros, ID.erro, `${ERRO_DE_CAMPO} mt-(--e-2)`);

  const borda = (...campos: Campo[]) =>
    campos.some((campo) => erros[campo] !== undefined) ? "border-coral" : "border-transparent";
  const prefereDescrever = edicao.genero === "OUTRO";
  const salvarIndisponivel = !consentimento || salvar.isPending;
  const descricaoDoSalvar = [
    ...(consentimento ? [] : [ID.semConsentimento]),
    ...(erroGeral === "" ? [] : [ID.erroGeral]),
  ].join(" ");

  return (
    <>
      <p className="mt-(--e-4) mb-0 px-(--e-4) py-(--e-3) border-l-4 border-ambar rounded-(--raio) bg-noite-950 text-marfim leading-[1.5] max-w-[60ch]">
        {NAMORO.aviso}
      </p>
      <form className="mt-(--e-6) grid gap-(--e-6)" noValidate onSubmit={enviar}>
        <fieldset
          className={`${GRUPO} ${borda("genero", "descricaoGenero")}`}
          // O erro da descrição fica só no campo dela; no grupo, seria lido duas vezes.
          aria-describedby={descricao("genero", ID.dicaDoGenero)}
        >
          <legend className={LEGENDA}>{NAMORO.rotuloDoGenero}</legend>
          <p className={`${DICA} mb-(--e-2)`} id={ID.dicaDoGenero}>
            {NAMORO.dicaDoGenero}
          </p>
          {GENEROS.map((genero, indice) => {
            const marcado = edicao.genero === genero;
            return (
              <label key={genero} className={OPCAO}>
                <input
                  ref={indice === 0 ? primeiroGenero : undefined}
                  className={MARCA}
                  type="radio"
                  name="genero"
                  value={genero}
                  checked={marcado}
                  onChange={() => {
                    escolherGenero(genero);
                  }}
                  aria-invalid={invalido("genero")}
                />
                {ROTULO_DO_GENERO[genero]}
              </label>
            );
          })}
          {erroDo("genero")}
          {prefereDescrever && (
            <div className={`${CAMPO} mt-(--e-2)`}>
              <label className={ROTULO} htmlFor={ID.descricaoGenero}>
                {NAMORO.rotuloDaDescricao}
              </label>
              {/* Sem maxLength: ele conta unidades de UTF-16, e o limite do servidor é em caracteres. */}
              <input
                ref={campoDaDescricao}
                className={ENTRADA_DE_TEXTO}
                id={ID.descricaoGenero}
                name="descricaoGenero"
                type="text"
                value={edicao.descricaoGenero}
                onChange={(evento) => {
                  mudar({ descricaoGenero: evento.target.value });
                }}
                aria-describedby={descricao("descricaoGenero", ID.dicaDaDescricao)}
                aria-invalid={invalido("descricaoGenero")}
              />
              <p className={DICA} id={ID.dicaDaDescricao}>
                {NAMORO.dicaDaDescricao}
              </p>
              {erroDo("descricaoGenero")}
            </div>
          )}
        </fieldset>

        <fieldset className={`${GRUPO} ${borda("interesses")}`} aria-describedby={descricao("interesses")}>
          <legend className={LEGENDA}>{NAMORO.rotuloDosInteresses}</legend>
          {GENEROS.map((genero, indice) => (
            <label key={genero} className={OPCAO}>
              <input
                ref={indice === 0 ? primeiroInteresse : undefined}
                className={MARCA}
                type="checkbox"
                name="interesses"
                value={genero}
                checked={edicao.interesses.includes(genero)}
                onChange={(evento) => {
                  alternarInteresse(genero, evento.target.checked);
                }}
                aria-invalid={invalido("interesses")}
              />
              {ROTULO_DO_INTERESSE[genero]}
            </label>
          ))}
          {erroDo("interesses")}
        </fieldset>

        <div className="grid gap-(--e-2)">
          <label className={`${OPCAO} font-semibold`}>
            <input
              className={MARCA}
              type="checkbox"
              name="consentimento"
              checked={consentimento}
              onChange={(evento) => {
                setSituacao("editando");
                setConsentimento(evento.target.checked);
              }}
              aria-describedby={dados === null ? undefined : ID.concordouEm}
            />
            {NAMORO.consentimento}
          </label>
          {dados !== null && (
            <p className={`${DICA} pl-[calc(1.25rem+var(--e-3))]`} id={ID.concordouEm}>
              {NAMORO.concordouEm(DATA_DO_CONSENTIMENTO.format(new Date(dados.consentidoEm)))}
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
            {salvar.isPending ? NAMORO.salvando : NAMORO.salvar}
          </button>
          {!consentimento && (
            <p className={DICA} id={ID.semConsentimento}>
              {NAMORO.semConsentimento}
            </p>
          )}
          <p className={STATUS} role="status">
            {situacao === "salvos" ? NAMORO.salvos : ""}
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

      {/* Sem dados gravados, não há o que apagar. */}
      {dados !== null && (
        <div className="mt-(--e-8) pt-(--e-6) border-t border-noite-700">
          <button
            type="button"
            className={`${BOTAO_SECUNDARIO_SEM_MARGEM} text-marfim`}
            onClick={() => {
              setErroGeral("");
              setSituacao("confirmando");
            }}
          >
            {NAMORO.apagar}
          </button>
          <DialogoDeConfirmacao
            aberto={situacao === "confirmando"}
            titulo={NAMORO.tituloDoApagar}
            texto={NAMORO.textoDoApagar}
            acao={apagar.isPending ? NAMORO.apagando : NAMORO.acaoDoApagar}
            aoConfirmar={seDisponivel(apagar.isPending, () => {
              apagar.mutate();
            })}
            aoCancelar={() => {
              setSituacao("editando");
            }}
            enviando={apagar.isPending}
          />
        </div>
      )}
    </>
  );
}
