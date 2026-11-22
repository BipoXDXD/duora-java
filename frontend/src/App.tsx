// Raiz da composição: recebe o cenário já carregado, cria a sessão, o aviso e o som uma vez, liga a
// sessão aos componentes e guarda os efeitos que o main.js antigo fazia ao iniciar (céu pintado, Esc, som).

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { api } from "./api/cliente";
import { prepararCeuPintado } from "./cena/ceu";
import { criarSom, type Som } from "./cena/som";
import { ProvedorDeCenario } from "./cenario/contexto";
import type { CenarioFront } from "./cenario/tipos";
import { Cena, type CenaRef } from "./componentes/Cena";
import { Moldura } from "./componentes/Moldura";
import { Painel } from "./componentes/Painel";
import { Topo } from "./componentes/Topo";
import { type Aviso, criarAviso } from "./sessao/aviso";
import { criarSessao, type EstadoDaSessao, type Sessao } from "./sessao/sessao";
import { usePassagemAoVivo } from "./usePassagemAoVivo";
import { marcasDaCena, useSessao, visaoDaCena, visaoDaTela } from "./useSessao";

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
 * onde o navegador deixa criar o áudio. O acorde só toca na passagem para a noite vista ao vivo, não
 * ao recarregar já revelado.
 */
function useSomDaSessao(som: Som, estado: EstadoDaSessao): void {
  const { somLigado } = estado;
  useEffect(() => {
    if (!somLigado) {
      som.definir(false);
    }
  }, [som, somLigado]);

  const passagemAoVivo = usePassagemAoVivo(estado);
  useEffect(() => {
    if (passagemAoVivo) {
      som.revelacao();
    }
  }, [som, passagemAoVivo]);
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
  const ceuPintado = useCeuPintado(cenario);
  useSomDaSessao(som, estado);
  useEscCancelaSelecao(sessao);

  const visao = visaoDaTela(estado);
  return (
    <ProvedorDeCenario value={cenario}>
      <Moldura momento={estado.momento} confirmandoSaida={estado.confirmandoSaida} ceuPintado={ceuPintado}>
        <Topo
          mostrarSair={visao !== null}
          nomeParceiro={visao?.parceiro?.nome ?? null}
          confirmando={estado.confirmandoSaida}
          enviandoSaida={estado.enviando === "sair"}
          aoAlternarSaida={estado.confirmandoSaida ? sessao.fecharSaida : sessao.abrirSaida}
          aoContinuar={sessao.fecharSaida}
          aoEncerrar={() => void sessao.encerrar()}
        />
        <Cena
          ref={refCena}
          visao={visaoDaCena(estado)}
          marcas={marcasDaCena(estado)}
          enviando={estado.enviando !== null}
          momento={estado.momento}
          ladoTelescopio={estado.ladoTelescopio}
          cenario={cenario}
          aoEscolherPeca={(peca, minha) => void sessao.escolherPeca(peca, minha)}
          aoEscolherLugar={(lugar, bloqueio) => void sessao.escolherLugar(lugar, bloqueio)}
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
        />
      </Moldura>
    </ProvedorDeCenario>
  );
}
