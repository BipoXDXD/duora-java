import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement, type SubmitEvent } from "react";
import { ErroHttp } from "../../api/http";
import {
  salvarPreferencias,
  versaoObsoleta,
  type PreferenciasDeEncontro,
  type Raio,
  RAIOS,
} from "../../api/perfil";
import { BOTAO_PRIMARIO, DICA, ENTRADA_DE_TEXTO, ROTULO, TITULO_DA_CASCA } from "../../componentes/estilos";
import { consultaDaLocalizacao, consultaDasPreferencias } from "../../rotas/consultas";
import { raioDisponivel, transicao, type EtapaDaLocalizacao, type EventoDaLocalizacao } from "./etapasDaLocalizacao";
import { useTravaDeEnvio } from "./useTravaDeEnvio";
import { Localizacao } from "./Localizacao";
import { PREFERENCIAS, ROTULO_DOS_RAIOS } from "./textos";

const IDADE_MINIMA = 18;
const IDADE_MAXIMA = 99;


/** As idades ficam como o texto digitado: a validação decide no envio. */
interface Edicao {
  versao: number | null;
  idadeMinima: string;
  idadeMaxima: string;
  raio: Raio;
  raioObrigatorio: boolean;
}

/** Qual das idades está fora: as duas quando "De" passa de "Até". */
interface IdadesInvalidas {
  de: boolean;
  ate: boolean;
}

const ID = {
  de: "preferencias-de",
  ate: "preferencias-ate",
  erroDaIdade: "preferencias-erro-idade",
  dicaDaDistancia: "preferencias-dica-distancia",
  erroDaDistancia: "preferencias-erro-distancia",
  erroGeral: "preferencias-erro",
} as const;

const GRUPO = "m-0 p-0 min-w-0 border-0 border-l-4 pl-(--e-3)";
const LEGENDA = `${ROTULO} p-0 mb-(--e-2)`;
const OPCAO = "flex items-center gap-(--e-3) min-h-[44px] leading-[1.45]";
const MARCA = "size-5 flex-none accent-ambar";
const ERRO = "m-0 text-[0.875rem] leading-[1.45] text-coral";

function deServidor(p: PreferenciasDeEncontro): Edicao {
  return {
    versao: p.versao,
    idadeMinima: String(p.idadeMinima),
    idadeMaxima: String(p.idadeMaxima),
    raio: p.raio,
    raioObrigatorio: p.raioObrigatorio,
  };
}

function idade(texto: string): number | null {
  const n = /^\d{1,3}$/.test(texto.trim()) ? Number(texto.trim()) : Number.NaN;
  return n >= IDADE_MINIMA && n <= IDADE_MAXIMA ? n : null;
}

function validarIdades(e: Edicao): IdadesInvalidas | null {
  const de = idade(e.idadeMinima);
  const ate = idade(e.idadeMaxima);
  if (de !== null && ate !== null) {
    return de <= ate ? null : { de: true, ate: true };
  }
  return { de: de === null, ate: ate === null };
}

/** Sem raio, não há o que exigir: o servidor grava `raioObrigatorio` falso, e a tela manda o mesmo. */
function paraEnvio(e: Edicao): PreferenciasDeEncontro {
  return {
    versao: e.versao,
    idadeMinima: Number(e.idadeMinima.trim()),
    idadeMaxima: Number(e.idadeMaxima.trim()),
    raio: e.raio,
    raioObrigatorio: e.raio === "SEM_LIMITE" ? false : e.raioObrigatorio,
  };
}

/**
 * Preferências de encontro: a faixa de idade, a localização e a distância. O raio só se escolhe com
 * a localização ativa; perder a localização (falha, desativação ou recusa do servidor) volta a
 * distância para Sem limite.
 */
export function Preferencias(): ReactElement {
  const clienteDeConsulta = useQueryClient();
  // O loader da rota já leu as duas: a tela não pede de novo ao montar.
  const { data: preferencias } = useSuspenseQuery({ ...consultaDasPreferencias, refetchOnMount: false });
  const { data: localizacao } = useSuspenseQuery({ ...consultaDaLocalizacao, refetchOnMount: false });
  const [edicao, setEdicao] = useState<Edicao>(() => deServidor(preferencias));
  const [etapa, setEtapa] = useState<EtapaDaLocalizacao>(() =>
    localizacao.consentida ? { tipo: "ativa" } : { tipo: "inativa" },
  );
  const [idadesInvalidas, setIdadesInvalidas] = useState<IdadesInvalidas | null>(null);
  const [erroDaDistancia, setErroDaDistancia] = useState("");
  const [erroGeral, setErroGeral] = useState("");
  const [salvas, setSalvas] = useState(false);
  const trava = useTravaDeEnvio();
  const campoDe = useRef<HTMLInputElement>(null);
  const campoAte = useRef<HTMLInputElement>(null);
  const semLimite = useRef<HTMLInputElement>(null);
  const alerta = useRef<HTMLParagraphElement>(null);
  const focarEm = useRef<"de" | "ate" | "semLimite" | "alerta" | null>(null);

  useEffect(() => {
    if (focarEm.current === null) {
      return;
    }
    const alvo = { de: campoDe, ate: campoAte, semLimite, alerta }[focarEm.current].current;
    focarEm.current = null;
    alvo?.focus();
  });

  const comRaio = raioDisponivel(etapa);

  function despachar(evento: EventoDaLocalizacao) {
    setEtapa((atual) => transicao(atual, evento));
  }

  function mudar(parte: Partial<Edicao>) {
    setSalvas(false);
    setEdicao((atual) => ({ ...atual, ...parte }));
  }

  /**
   * A distância volta para Sem limite, e as preferências são lidas de novo: a desativação muda a
   * versão no servidor, e o próximo salvamento precisa dela.
   */
  async function voltarParaSemLimite() {
    mudar({ raio: "SEM_LIMITE", raioObrigatorio: false });
    await clienteDeConsulta.invalidateQueries({ queryKey: consultaDasPreferencias.queryKey, exact: true });
    const novas = clienteDeConsulta.getQueryData(consultaDasPreferencias.queryKey);
    if (novas !== undefined) {
      setEdicao((atual) => ({ ...atual, versao: novas.versao }));
    }
  }

  async function recarregarAposConflito() {
    try {
      const novas = await clienteDeConsulta.query(consultaDasPreferencias);
      setEdicao(deServidor(novas));
      setErroGeral(PREFERENCIAS.versaoObsoleta);
    } catch {
      setErroGeral(PREFERENCIAS.falha);
    }
    focarEm.current = "alerta";
  }

  const salvar = useMutation({
    mutationFn: salvarPreferencias,
    onSuccess: (novas) => {
      clienteDeConsulta.setQueryData(consultaDasPreferencias.queryKey, novas);
      setEdicao(deServidor(novas));
      setSalvas(true);
    },
    onError: async (erro) => {
      if (versaoObsoleta(erro)) {
        await recarregarAposConflito();
        return;
      }
      if (erro instanceof ErroHttp && erro.status === 422 && erro.codigo === "LOCALIZACAO_NECESSARIA") {
        // O servidor não tem a localização: a tela deixa de oferecer o raio.
        setEtapa({ tipo: "inativa" });
        setErroDaDistancia(erro.mensagem ?? PREFERENCIAS.ativeALocalizacao);
        focarEm.current = "semLimite";
        void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaLocalizacao.queryKey, exact: true });
        await voltarParaSemLimite();
        return;
      }
      if (erro instanceof ErroHttp && erro.status === 422 && erro.codigo === "FAIXA_ETARIA_INVALIDA") {
        setIdadesInvalidas({ de: true, ate: true });
        focarEm.current = "de";
        return;
      }
      setErroGeral(PREFERENCIAS.falha);
      focarEm.current = "alerta";
    },
    onSettled: () => {
      trava.abrir();
    },
  });

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (trava.fechada()) {
      return;
    }
    setSalvas(false);
    setErroGeral("");
    setErroDaDistancia("");
    const invalidas = validarIdades(edicao);
    setIdadesInvalidas(invalidas);
    if (invalidas !== null) {
      focarEm.current = invalidas.de ? "de" : "ate";
      return;
    }
    trava.fechar();
    salvar.mutate(paraEnvio(edicao));
  }

  function escolherRaio(raio: Raio) {
    // Sem localização, só Sem limite vale: o clique nos outros não muda nada.
    if (raio !== "SEM_LIMITE" && !comRaio) {
      return;
    }
    setErroDaDistancia("");
    mudar({ raio });
  }

  const raioEscolhido = edicao.raio !== "SEM_LIMITE";
  const descricaoDaIdade = (invalida: boolean | undefined) => (invalida === true ? ID.erroDaIdade : undefined);

  return (
    <>
      <title>{PREFERENCIAS.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{PREFERENCIAS.titulo}</h1>
      <form className="mt-(--e-6) grid gap-(--e-6)" noValidate onSubmit={enviar}>
        <fieldset className={`${GRUPO} ${idadesInvalidas === null ? "border-transparent" : "border-coral"}`}>
          <legend className={LEGENDA}>{PREFERENCIAS.rotuloDaIdade}</legend>
          <div className="flex flex-wrap items-end gap-(--e-4)">
            {(
              [
                { campo: "de", id: ID.de, ref: campoDe, rotulo: PREFERENCIAS.de, chave: "idadeMinima" },
                { campo: "ate", id: ID.ate, ref: campoAte, rotulo: PREFERENCIAS.ate, chave: "idadeMaxima" },
              ] as const
            ).map(({ campo, id, ref, rotulo, chave }) => (
              <div key={campo} className="grid gap-(--e-2)">
                <label className={ROTULO} htmlFor={id}>
                  {rotulo}
                </label>
                <input
                  ref={ref}
                  className={`${ENTRADA_DE_TEXTO} w-[6.5rem] tabular-nums`}
                  id={id}
                  name={chave}
                  type="number"
                  inputMode="numeric"
                  min={IDADE_MINIMA}
                  max={IDADE_MAXIMA}
                  value={edicao[chave]}
                  onChange={(evento) => {
                    mudar({ [chave]: evento.target.value });
                  }}
                  aria-describedby={descricaoDaIdade(idadesInvalidas?.[campo])}
                  aria-invalid={idadesInvalidas?.[campo] === true ? true : undefined}
                />
              </div>
            ))}
          </div>
          {idadesInvalidas !== null && (
            <p className={`${ERRO} mt-(--e-2)`} id={ID.erroDaIdade}>
              {PREFERENCIAS.idadeInvalida}
            </p>
          )}
        </fieldset>

        <Localizacao
          etapa={etapa}
          despachar={despachar}
          aoPerder={() => {
            void voltarParaSemLimite();
          }}
        />

        <fieldset
          className={`${GRUPO} ${erroDaDistancia === "" ? "border-transparent" : "border-coral"}`}
          aria-describedby={erroDaDistancia === "" ? undefined : ID.erroDaDistancia}
        >
          <legend className={LEGENDA}>{PREFERENCIAS.rotuloDaDistancia}</legend>
          {!comRaio && (
            <p className={`${DICA} mb-(--e-2)`} id={ID.dicaDaDistancia}>
              {PREFERENCIAS.ativeALocalizacao}
            </p>
          )}
          {RAIOS.map((raio) => {
            const indisponivel = raio !== "SEM_LIMITE" && !comRaio;
            return (
              <label
                key={raio}
                className={`${OPCAO} ${indisponivel ? "text-texto-3 cursor-not-allowed" : "cursor-pointer"}`}
              >
                <input
                  ref={raio === "SEM_LIMITE" ? semLimite : undefined}
                  className={`${MARCA} ${indisponivel ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
                  type="radio"
                  name="raio"
                  value={raio}
                  checked={edicao.raio === raio}
                  onChange={() => {
                    escolherRaio(raio);
                  }}
                  aria-disabled={indisponivel ? true : undefined}
                  aria-describedby={indisponivel ? ID.dicaDaDistancia : undefined}
                />
                {ROTULO_DOS_RAIOS[raio]}
              </label>
            );
          })}
          {erroDaDistancia !== "" && (
            <p className={`${ERRO} mt-(--e-2)`} id={ID.erroDaDistancia}>
              {erroDaDistancia}
            </p>
          )}
        </fieldset>

        {raioEscolhido && (
          <fieldset className={`${GRUPO} border-transparent`}>
            <legend className={LEGENDA}>{PREFERENCIAS.rotuloDoComoUsar}</legend>
            {[true, false].map((obrigatorio) => (
              <label key={String(obrigatorio)} className={`${OPCAO} cursor-pointer`}>
                <input
                  className={`${MARCA} cursor-pointer`}
                  type="radio"
                  name="raioObrigatorio"
                  value={String(obrigatorio)}
                  checked={edicao.raioObrigatorio === obrigatorio}
                  onChange={() => {
                    mudar({ raioObrigatorio: obrigatorio });
                  }}
                />
                {obrigatorio ? PREFERENCIAS.soDentroDoRaio : PREFERENCIAS.maisPertoPrimeiro}
              </label>
            ))}
          </fieldset>
        )}

        <div className="grid gap-(--e-3)">
          <button
            type="submit"
            className={`w-full ${BOTAO_PRIMARIO}`}
            aria-disabled={salvar.isPending ? true : undefined}
            aria-describedby={erroGeral === "" ? undefined : ID.erroGeral}
          >
            {salvar.isPending ? PREFERENCIAS.salvando : PREFERENCIAS.salvar}
          </button>
          <p className="m-0 text-[1.0625rem] leading-[1.5] text-marfim empty:hidden" role="status">
            {salvas ? PREFERENCIAS.salvas : ""}
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
    </>
  );
}
