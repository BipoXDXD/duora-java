import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useReducer, useRef, type ChangeEvent, type ReactElement } from "react";
import { novaChaveDeIdempotencia } from "../../api/http";
import { enviarFoto, type FotoDoPerfil } from "../../api/perfil";
import { FotoOuSilhueta } from "../../componentes/FotoOuSilhueta";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO_SEM_MARGEM, DICA, TEXTO_SECUNDARIO } from "../../componentes/estilos";
import { consultaDoMeuPerfil } from "../../rotas/consultas";
import { textoDeEspera } from "../conta/textos";
import {
  conferirNoCliente,
  ETAPA_INICIAL,
  TIPOS_ACEITOS,
  transicao,
  type AvisoDaFoto,
  type EtapaDaFoto,
  type EventoDaFoto,
} from "./etapasDaFoto";
import { lerPrevia } from "./lerPrevia";
import { FOTO } from "./textos";

interface Props {
  apelido: string | null;
  foto: FotoDoPerfil;
}

const ID = {
  titulo: "perfil-titulo-foto",
  seletor: "perfil-seletor-foto",
  dica: "perfil-dica-foto",
  politica: "perfil-politica-foto",
} as const;

const TITULO_DE_SECAO = "m-0 font-titulo font-semibold text-[1.375rem] leading-[1.2]";

/** O seletor de arquivo fica escondido; o rótulo faz o papel do botão e mostra o foco dele. */
const ROTULO_DO_SELETOR = [
  BOTAO_SECUNDARIO_SEM_MARGEM,
  "inline-flex items-center text-marfim",
  "peer-focus-visible:[outline:2px_solid_transparent] peer-focus-visible:outline-offset-2 peer-focus-visible:[box-shadow:var(--foco)]",
].join(" ");

function textoDoAviso(aviso: AvisoDaFoto): string {
  switch (aviso.tipo) {
    case "grande":
      return FOTO.grande;
    case "formato":
      return FOTO.formato;
    case "invalida":
      return FOTO.invalida;
    case "indisponivel":
      return FOTO.indisponivel;
    case "limite":
      return textoDeEspera(aviso.segundos);
    case "servidor":
      return aviso.mensagem;
  }
}

/** A situação da foto guardada: o que os outros veem enquanto a nova espera aprovação. */
function legenda(foto: FotoDoPerfil): string | null {
  if (foto.pendente !== null) {
    return foto.aprovada === null ? FOTO.aguardando : FOTO.novaAguardando;
  }
  if (foto.recusadaEm !== null) {
    return FOTO.recusada;
  }
  return foto.aprovada === null ? FOTO.semFoto : null;
}

/** O useReducer passa só a etapa e o evento; a chave nova vem do gerador padrão. */
function reduzir(etapa: EtapaDaFoto, evento: EventoDaFoto): EtapaDaFoto {
  return transicao(etapa, evento);
}

function avisoDa(etapa: EtapaDaFoto): AvisoDaFoto | undefined {
  return etapa.tipo === "parada" || etapa.tipo === "falhou" ? etapa.aviso : undefined;
}

/**
 * A foto do perfil: a guardada (a pendente, senão a aprovada) e o envio de uma nova, com a prévia
 * antes. O tamanho e o tipo declarado são conferidos no aparelho; o resto, no servidor.
 */
export function EnviarFoto({ apelido, foto }: Props): ReactElement {
  const clienteDeConsulta = useQueryClient();
  const [etapa, despachar] = useReducer(reduzir, ETAPA_INICIAL);
  // Trava síncrona: a etapa "enviando" só chega no render seguinte.
  const emEnvio = useRef(false);
  const confirmacao = useRef<HTMLParagraphElement>(null);
  const botaoDeEnviar = useRef<HTMLButtonElement>(null);

  const envio = useMutation({
    mutationFn: ({ arquivo, chave }: { arquivo: File; chave: string }) => enviarFoto(arquivo, chave),
    onSuccess: () => {
      despachar({ tipo: "enviado" });
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDoMeuPerfil.queryKey, exact: true });
    },
    onError: (erro) => {
      despachar({ tipo: "envioRecusado", erro });
    },
    onSettled: () => {
      emEnvio.current = false;
    },
  });

  useEffect(() => {
    if (etapa.tipo === "enviada") {
      confirmacao.current?.focus();
    }
  }, [etapa.tipo]);

  function escolher(evento: ChangeEvent<HTMLInputElement>) {
    const arquivo = evento.target.files?.[0];
    // Limpo, o seletor aceita de novo o mesmo arquivo.
    evento.target.value = "";
    if (arquivo === undefined) {
      return;
    }
    const aviso = conferirNoCliente(arquivo);
    if (aviso !== null) {
      despachar({ tipo: "recusadaNoCliente", aviso });
      return;
    }
    lerPrevia(arquivo).then(
      (previa) => {
        despachar({ tipo: "escolheu", arquivo, previa, chave: novaChaveDeIdempotencia() });
      },
      () => {
        despachar({ tipo: "previaFalhou" });
      },
    );
  }

  function enviar() {
    if (emEnvio.current || (etapa.tipo !== "previa" && etapa.tipo !== "falhou")) {
      return;
    }
    emEnvio.current = true;
    despachar({ tipo: "enviar" });
    envio.mutate({ arquivo: etapa.arquivo, chave: etapa.chave });
  }

  /** O botão some ao reenviar: o foco vai para o Enviar foto, que mostra o andamento. */
  function tentarDeNovo() {
    enviar();
    botaoDeEnviar.current?.focus();
  }

  const comPrevia = etapa.tipo === "previa" || etapa.tipo === "enviando" || etapa.tipo === "falhou";
  const enviando = etapa.tipo === "enviando";
  const aviso = avisoDa(etapa);
  const situacao = legenda(foto);

  return (
    <section aria-labelledby={ID.titulo} className="mt-(--e-8) pt-(--e-6) border-t border-noite-700 grid gap-(--e-4)">
      <h2 id={ID.titulo} className={TITULO_DE_SECAO}>
        {FOTO.titulo}
      </h2>
      {comPrevia ? (
        <FotoOuSilhueta url={etapa.previa} alt={FOTO.previa} tamanho="grande" carregamento="eager" />
      ) : (
        <FotoOuSilhueta
          url={foto.pendente?.nitida ?? foto.aprovada?.nitida ?? null}
          alt={FOTO.alt(apelido)}
          tamanho="grande"
        />
      )}
      {!comPrevia && situacao !== null && <p className={`${TEXTO_SECUNDARIO} leading-[1.5]`}>{situacao}</p>}
      <p className={`${TEXTO_SECUNDARIO} leading-[1.5] max-w-[60ch]`} id={ID.politica}>
        {FOTO.politica}
      </p>

      <div className="grid gap-(--e-3)">
        <div className="flex flex-wrap items-center gap-(--e-3)">
          {comPrevia && (
            <button
              type="button"
              ref={botaoDeEnviar}
              className={BOTAO_PRIMARIO}
              aria-disabled={enviando ? true : undefined}
              onClick={() => {
                if (!enviando) {
                  enviar();
                }
              }}
            >
              {enviando ? FOTO.enviando : FOTO.enviar}
            </button>
          )}
          <div>
            <input
              className="peer sr-only"
              id={ID.seletor}
              type="file"
              accept={TIPOS_ACEITOS.join(",")}
              aria-describedby={`${ID.dica} ${ID.politica}`}
              onChange={escolher}
            />
            <label htmlFor={ID.seletor} className={ROTULO_DO_SELETOR}>
              {comPrevia ? FOTO.escolherOutra : FOTO.escolher}
            </label>
          </div>
        </div>
        <p className={DICA} id={ID.dica}>
          {FOTO.dica}
        </p>
        <p
          ref={confirmacao}
          tabIndex={-1}
          className="m-0 text-[1.0625rem] leading-[1.5] text-marfim empty:hidden focus:not-focus-visible:[outline:none]"
          role="status"
        >
          {etapa.tipo === "enviada" ? FOTO.enviada : ""}
        </p>
        <p className="m-0 text-[0.9375rem] leading-[1.45] text-coral empty:hidden" role="alert">
          {aviso === undefined ? "" : textoDoAviso(aviso)}
        </p>
        {etapa.tipo === "falhou" && etapa.aviso.tipo === "indisponivel" && (
          <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={tentarDeNovo}>
            {FOTO.tentarDeNovo}
          </button>
        )}
      </div>
    </section>
  );
}
