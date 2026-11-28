import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactElement } from "react";
import { ativarLocalizacao, desativarLocalizacao } from "../../api/perfil";
import { BOTAO_SECUNDARIO_SEM_MARGEM, TEXTO_SECUNDARIO } from "../../componentes/estilos";
import { obterPosicao, type ResultadoDePosicao } from "../../geo/obterPosicao";
import { consultaDaLocalizacao } from "../../rotas/consultas";
import type { EtapaDaLocalizacao, EventoDaLocalizacao } from "./etapasDaLocalizacao";
import { LOCALIZACAO } from "./textos";

const ID_DO_TITULO = "preferencias-titulo-localizacao";

const TITULO_DE_SECAO = "m-0 font-titulo font-semibold text-[1.375rem] leading-[1.2]";

interface Props {
  etapa: EtapaDaLocalizacao;
  despachar: (evento: EventoDaLocalizacao) => void;
  /** A localização não ficou ativa (falha ou desativação): a distância volta para Sem limite. */
  aoPerder: () => void;
}

/** O aviso que a última ação deixou e que a etapa sozinha não diz. */
type Aviso = "desativada" | "falhaAoDesativar" | null;

/** As frases do status, na ordem: com a permissão negada, a falha diz como liberar. */
function frasesDoStatus(etapa: EtapaDaLocalizacao, aviso: Aviso): string[] {
  switch (etapa.tipo) {
    case "pedindo":
      return [LOCALIZACAO.esperando];
    case "gravando":
      return [LOCALIZACAO.gravando];
    case "desativando":
      return [];
    case "ativa":
      return aviso === "falhaAoDesativar" ? [LOCALIZACAO.falhaAoDesativar] : [];
    case "inativa":
      if (etapa.falha !== undefined) {
        return etapa.falha === "negada" ? [LOCALIZACAO.falhou, LOCALIZACAO.negada] : [LOCALIZACAO.falhou];
      }
      return aviso === "desativada" ? [LOCALIZACAO.desativada] : [];
  }
}

/**
 * Ligar e desligar a localização. O clique em "Usar minha localização" é o consentimento: a posição
 * é pedida, arredondada e enviada na mesma função, sem passar por estado, pelo DOM nem pelo console.
 */
export function Localizacao({ etapa, despachar, aoPerder }: Props): ReactElement {
  const clienteDeConsulta = useQueryClient();
  const [aviso, setAviso] = useState<Aviso>(null);
  // Trava síncrona: a etapa nova só chega no render seguinte.
  const ocupado = useRef(false);
  const botao = useRef<HTMLButtonElement>(null);
  // O botão some e outro aparece ao fim de cada ação: o foco vai para o novo.
  const focarNoBotao = useRef(false);

  useEffect(() => {
    if (
      !focarNoBotao.current ||
      etapa.tipo === "pedindo" ||
      etapa.tipo === "gravando" ||
      etapa.tipo === "desativando"
    ) {
      return;
    }
    focarNoBotao.current = false;
    botao.current?.focus();
  });

  async function usar() {
    if (ocupado.current) {
      return;
    }
    ocupado.current = true;
    setAviso(null);
    despachar({ tipo: "pedir" });
    try {
      // Um erro inesperado do navegador não pode deixar a tela presa em "Esperando a permissão".
      const resultado = await obterPosicao().catch((): ResultadoDePosicao => ({ ok: false, falha: "indisponivel" }));
      if (!resultado.ok) {
        despachar({ tipo: "posicaoFalhou", falha: resultado.falha });
        aoPerder();
        return;
      }
      despachar({ tipo: "posicaoObtida" });
      try {
        await ativarLocalizacao(resultado.coordenada);
      } catch {
        despachar({ tipo: "gravacaoFalhou" });
        aoPerder();
        return;
      }
      despachar({ tipo: "gravada" });
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaLocalizacao.queryKey, exact: true });
    } finally {
      ocupado.current = false;
      focarNoBotao.current = true;
    }
  }

  async function desativar() {
    if (ocupado.current) {
      return;
    }
    ocupado.current = true;
    setAviso(null);
    despachar({ tipo: "desativar" });
    try {
      await desativarLocalizacao();
      despachar({ tipo: "desativada" });
      setAviso("desativada");
      aoPerder();
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaLocalizacao.queryKey, exact: true });
    } catch {
      despachar({ tipo: "desativacaoFalhou" });
      setAviso("falhaAoDesativar");
    } finally {
      ocupado.current = false;
      focarNoBotao.current = true;
    }
  }

  const ligada = etapa.tipo === "ativa" || etapa.tipo === "desativando";
  const ocupada = etapa.tipo === "pedindo" || etapa.tipo === "gravando" || etapa.tipo === "desativando";
  const frases = frasesDoStatus(etapa, aviso);
  const problema = (etapa.tipo === "inativa" && etapa.falha !== undefined) || aviso === "falhaAoDesativar";

  return (
    <section aria-labelledby={ID_DO_TITULO} className="grid gap-(--e-3)">
      <h2 id={ID_DO_TITULO} className={TITULO_DE_SECAO}>
        {LOCALIZACAO.titulo}
      </h2>
      <p className={`${TEXTO_SECUNDARIO} leading-[1.5] max-w-[60ch]`}>{LOCALIZACAO.explicacao}</p>
      {ligada && (
        <p className="m-0 flex items-center gap-(--e-2) text-marfim leading-[1.45]">
          <span aria-hidden="true" className="size-2.5 flex-none rounded-full bg-ambar" />
          {LOCALIZACAO.ativa}
        </p>
      )}
      <button
        ref={botao}
        type="button"
        className={`${BOTAO_SECUNDARIO_SEM_MARGEM} justify-self-start text-marfim`}
        aria-disabled={ocupada ? true : undefined}
        onClick={() => {
          if (ocupada) {
            return;
          }
          void (ligada ? desativar() : usar());
        }}
      >
        {ligada ? (etapa.tipo === "desativando" ? LOCALIZACAO.desativando : LOCALIZACAO.desativar) : LOCALIZACAO.usar}
      </button>
      {/* Sempre na árvore: o leitor de tela anuncia cada mudança. */}
      <p
        className={`m-0 text-[0.9375rem] leading-[1.45] max-w-[60ch] empty:hidden ${problema ? "text-ambar" : "text-texto-2"}`}
        role="status"
      >
        {frases.map((frase, indice) => (
          <span key={frase}>
            {indice > 0 && " "}
            {frase}
          </span>
        ))}
      </p>
    </section>
  );
}
