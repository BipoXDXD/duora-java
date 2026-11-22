// Raiz da composição: cria a sessão, o aviso e o som uma vez, liga a sessão aos componentes e
// guarda os efeitos que o main.js antigo fazia ao iniciar (cena.json, céu pintado, Esc, som).

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { api } from "./api/cliente";
import { prepararCeuPintado } from "./cena/ceu";
import { criarSom, type Som } from "./cena/som";
import { Cena, type CenaRef } from "./componentes/Cena";
import { Moldura } from "./componentes/Moldura";
import { Painel } from "./componentes/Painel";
import { Topo } from "./componentes/Topo";
import { carregarCena, type Geometria, type Momento } from "./dominio/dados";
import { type Aviso, criarAviso } from "./sessao/aviso";
import { criarSessao, type EstadoDaSessao, type Sessao } from "./sessao/sessao";
import { useSessao, visaoDaCena, visaoDaTela } from "./useSessao";

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

/** Busca o cena.json e só então inicia a sessão, como antes; para o polling ao desmontar. */
function useGeometria(sessao: Sessao, aviso: Aviso): Geometria | null {
  const [geometria, setGeometria] = useState<Geometria | null>(null);
  useEffect(() => {
    let montado = true;
    carregarCena().then(
      (carregada) => {
        if (montado) {
          setGeometria(carregada);
          sessao.iniciar();
        }
      },
      (erro: unknown) => {
        if (montado) {
          const mensagem = erro instanceof Error ? erro.message : String(erro);
          aviso.avisar(`Não foi possível carregar a cena: ${mensagem}`);
        }
      },
    );
    return () => {
      montado = false;
      sessao.parar();
    };
  }, [sessao, aviso]);
  return geometria;
}

/** .ceu-pintado só quando as imagens do céu carregaram. */
function useCeuPintado(): boolean {
  const [pintado, setPintado] = useState(false);
  useEffect(() => {
    let montado = true;
    void prepararCeuPintado().then((carregou) => {
      if (montado) {
        setPintado(carregou);
      }
    });
    return () => {
      montado = false;
    };
  }, []);
  return pintado;
}

/**
 * O som desliga junto com o estado (a sessão terminou e o botão sumiu); ligar fica no clique, que é
 * onde o navegador deixa criar o áudio. O acorde só toca na passagem para a noite vista ao vivo, não
 * ao recarregar já revelado.
 */
function useSomDaSessao(som: Som, estado: EstadoDaSessao): void {
  const { somLigado, momento, tela } = estado;
  useEffect(() => {
    if (!somLigado) {
      som.definir(false);
    }
  }, [som, somLigado]);

  const momentoVisto = useRef<Momento | null>(null);
  useEffect(() => {
    if (tela === "carregando") {
      return;
    }
    if (momentoVisto.current === "crepusculo" && momento === "noite") {
      som.revelacao();
    }
    momentoVisto.current = momento;
  }, [som, tela, momento]);
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

export function App({ convite }: { convite: string | null }) {
  const refAviso = useRef<HTMLParagraphElement>(null);
  const refCena = useRef<CenaRef>(null);
  const [aviso] = useState(criarAvisoAdiado);
  const [sessao] = useState(() => criarSessao({ api, aviso, convite }));
  const [som] = useState(criarSom);

  useLayoutEffect(() => {
    if (refAviso.current !== null) {
      aviso.ligar(refAviso.current);
    }
  }, [aviso]);

  const estado = useSessao(sessao, refCena);
  const geometria = useGeometria(sessao, aviso);
  const ceuPintado = useCeuPintado();
  useSomDaSessao(som, estado);
  useEscCancelaSelecao(sessao);

  const visao = visaoDaTela(estado);
  return (
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
        selecionada={estado.tela === "montagem" ? estado.selecionada : null}
        enviando={estado.enviando !== null}
        momento={estado.momento}
        ladoTelescopio={estado.ladoTelescopio}
        geometria={geometria}
        aoEscolherPeca={(peca, minha) => void sessao.escolherPeca(peca, minha)}
        aoEscolherLugar={(lugar, bloqueio) => void sessao.escolherLugar(lugar, bloqueio)}
        aoMeteoro={() => {
          som.meteoro();
        }}
      />
      <Painel
        estado={estado}
        refAviso={refAviso}
        aoEnviarApelido={(apelido) => void sessao.enviarApelido(apelido)}
        aoCorrigirApelido={sessao.limparErroApelido}
        aoCopiarConvite={(texto) => {
          aviso.avisar(texto);
        }}
        aoCriarOutro={sessao.criarOutro}
        aoEscolherPeca={(peca) => void sessao.escolherPeca(peca, true)}
        aoRetirar={() => void sessao.retirar()}
        aoConfirmar={() => void sessao.confirmar()}
        aoAjustar={() => void sessao.ajustar()}
        aoAlternarSom={() => {
          sessao.alternarSom();
          som.definir(sessao.obter().somLigado);
        }}
      />
    </Moldura>
  );
}
