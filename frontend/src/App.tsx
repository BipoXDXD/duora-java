// Raiz da composição: recebe o cenário já carregado, cria a sessão, o aviso e o som uma vez, liga a
// sessão aos componentes e guarda os efeitos que o main.js antigo fazia ao iniciar (céu pintado, Esc, som).

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { api } from "./api/cliente";
import { prepararCeuPintado } from "./cena/ceu";
import { ladoDosMeteoros } from "./cena/meteoros";
import { criarSom, type Som } from "./cena/som";
import { ProvedorDeCenario } from "./cenario/contexto";
import type { CenarioFront } from "./cenario/tipos";
import { Cena, type CenaRef } from "./componentes/Cena";
import { Moldura } from "./componentes/Moldura";
import { Painel } from "./componentes/Painel";
import { Topo } from "./componentes/Topo";
import { lerConvite } from "./convite";
import { type Aviso, criarAviso } from "./sessao/aviso";
import { criarSessao, type EstadoDaSessao, type Sessao } from "./sessao/sessao";
import { useAlturaDoParEAviso } from "./useAlturaDoParEAviso";
import { useFocoDaTelaCheia } from "./useFocoDaTelaCheia";
import { marcasDaCena, useSessao, visaoDaCena, visaoDaTela } from "./useSessao";
import { useSomDaRevelacao } from "./useSomDaRevelacao";
import { useTelaCheia } from "./useTelaCheia";

interface AvisoAdiado extends Aviso {
  /** Liga o aviso ao #aviso; antes disso não há onde anunciar (e a sessão ainda não começou). */
  ligar: (el: HTMLElement) => void;
}

/** A sessão nasce no primeiro render, quando o #aviso ainda não existe: ela recebe este adaptador. */
function criarAvisoAdiado(): AvisoAdiado {
  let real: Aviso | null = null;
  return {
    ligar(el) {
      real = criarAviso(el);
    },
    avisar(texto, tipo) {
      real?.avisar(texto, tipo);
    },
    avisarComDetalhe(fixo, variavel, tipo) {
      real?.avisarComDetalhe(fixo, variavel, tipo);
    },
  };
}

/** A sessão consulta a sala enquanto o app está montado; parar() também atende a remontagem do StrictMode. */
function useSessaoIniciada(sessao: Sessao): void {
  useEffect(() => {
    sessao.iniciar();
    return () => {
      sessao.parar();
    };
  }, [sessao]);
}

/** Um #convite= aberto nesta aba depois da carga (colado na barra, link na mesma aba) vai à sessão. */
function useConviteNaMesmaAba(sessao: Sessao): void {
  useEffect(() => {
    const aoMudarHash = () => {
      const convite = lerConvite(location, history);
      if (convite !== null) {
        void sessao.receberConvite(convite);
      }
    };
    window.addEventListener("hashchange", aoMudarHash);
    return () => {
      window.removeEventListener("hashchange", aoMudarHash);
    };
  }, [sessao]);
}

/** .ceu-pintado só quando as imagens do céu carregaram. */
function useCeuPintado(cenario: CenarioFront): boolean {
  const [pintado, setPintado] = useState(false);
  useEffect(() => {
    let montado = true;
    void prepararCeuPintado(cenario).then((carregou) => {
      if (montado) {
        setPintado(carregou);
      }
    });
    return () => {
      montado = false;
    };
  }, [cenario]);
  return pintado;
}

/**
 * O som desliga junto com o estado (a sessão terminou e o botão sumiu); ligar fica no clique, que é
 * onde o navegador deixa criar o áudio. O primeiro toque ou tecla em qualquer lugar já destrava o
 * áudio, para as notas do rádio tocarem quando a noite chega pelo polling, sem gesto. O acorde e as
 * notas só tocam na passagem para a noite vista ao vivo, não ao recarregar já revelado.
 */
function useSomDaSessao(som: Som, cenario: CenarioFront, estado: EstadoDaSessao): void {
  const { somLigado } = estado;
  useEffect(() => {
    if (!somLigado) {
      som.definir(false);
    }
  }, [som, somLigado]);

  useEffect(() => {
    const destravar = () => {
      som.desbloquear();
      document.removeEventListener("pointerdown", destravar);
      document.removeEventListener("keydown", destravar);
    };
    document.addEventListener("pointerdown", destravar);
    document.addEventListener("keydown", destravar);
    return () => {
      document.removeEventListener("pointerdown", destravar);
      document.removeEventListener("keydown", destravar);
    };
  }, [som]);

  useSomDaRevelacao(som, cenario, estado);
}

/** Esc em qualquer lugar desfaz a seleção da peça (o Topo segura o Esc da confirmação de saída). */
function useEscCancelaSelecao(sessao: Sessao): void {
  useEffect(() => {
    const aoTeclar = (evento: KeyboardEvent) => {
      if (evento.key === "Escape") {
        sessao.cancelarSelecao();
      }
    };
    document.addEventListener("keydown", aoTeclar);
    return () => {
      document.removeEventListener("keydown", aoTeclar);
    };
  }, [sessao]);
}

export function App({ convite, cenario }: { convite: string | null; cenario: CenarioFront }) {
  const refAviso = useRef<HTMLParagraphElement>(null);
  const refCena = useRef<CenaRef>(null);
  const [aviso] = useState(criarAvisoAdiado);
  const [sessao] = useState(() => criarSessao({ api, aviso, convite, cenario }));
  const [som] = useState(() => criarSom(cenario));

  useLayoutEffect(() => {
    if (refAviso.current !== null) {
      aviso.ligar(refAviso.current);
    }
  }, [aviso]);

  const estado = useSessao(sessao, refCena);
  useSessaoIniciada(sessao);
  useConviteNaMesmaAba(sessao);
  const ceuPintado = useCeuPintado(cenario);
  useSomDaSessao(som, cenario, estado);
  useEscCancelaSelecao(sessao);
  const telaCheia = useTelaCheia(estado);
  useFocoDaTelaCheia(telaCheia.telaCheia);
  useAlturaDoParEAviso();

  const visao = visaoDaTela(estado);
  const visaoDaCenaAtual = visaoDaCena(estado);
  return (
    <ProvedorDeCenario value={cenario}>
      <Moldura
        momento={estado.momento}
        confirmandoSaida={estado.confirmandoSaida}
        ceuPintado={ceuPintado}
        telaCheia={telaCheia.telaCheia}
      >
        <Topo
          mostrarSair={visao !== null}
          mostrarLinkDaConta={estado.tela !== "carregando"}
          nomeParceiro={visao?.parceiro?.nome ?? null}
          confirmando={estado.confirmandoSaida}
          enviandoSaida={estado.enviando === "sair"}
          aoAlternarSaida={estado.confirmandoSaida ? sessao.fecharSaida : sessao.abrirSaida}
          aoContinuar={sessao.fecharSaida}
          aoEncerrar={() => void sessao.encerrar()}
        />
        <Cena
          ref={refCena}
          visao={visaoDaCenaAtual}
          marcas={marcasDaCena(estado)}
          enviando={estado.enviando !== null}
          momento={estado.momento}
          ladoMeteoros={ladoDosMeteoros(cenario, visaoDaCenaAtual?.pecas ?? [], estado.ladoTelescopio)}
          cenario={cenario}
          aoEscolherPeca={(peca, minha) => void sessao.escolherPeca(peca, minha)}
          aoEscolherLugar={(lugar, bloqueio) => void sessao.escolherLugar(lugar, bloqueio)}
          aoTocarAbertura={() => void sessao.tocarAbertura()}
          aoMeteoro={() => {
            som.meteoro();
          }}
        />
        <Painel
          estado={estado}
          refAviso={refAviso}
          acoes={sessao}
          aoCopiarConvite={(texto) => {
            aviso.avisar(texto);
          }}
          aoAlternarSom={() => {
            sessao.alternarSom();
            som.definir(sessao.obter().somLigado);
          }}
          telaCheia={telaCheia}
        />
      </Moldura>
    </ProvedorDeCenario>
  );
}
