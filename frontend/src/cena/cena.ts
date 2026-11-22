// Desenha a cena do cenário a partir da visão da sala. Não decide regras: usa os lugares
// permitidos que a visão traz para a peça selecionada.

import type { CenarioFront } from "../cenario/tipos";
import { escolherVariante, type MedidasSprite, type Momento, pct, type Ponto } from "../dominio/dados";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { type CamadaDoEfeito, criarElementosDoEfeito } from "./efeitos";

const MOMENTOS: readonly Momento[] = ["crepusculo", "noite"];

/**
 * Escolhe a família pela largura do palco já medido × densidade da tela e aponta os dois
 * fundos do cenário para ela, entre as variantes do cena.json. Devolve a largura escolhida.
 */
export function mostrarTerraco(palco: HTMLElement, c: CenarioFront): number {
  const variante = escolherVariante(
    palco.getBoundingClientRect().width || window.innerWidth,
    window.devicePixelRatio || 1,
    c.geometria.variantes,
  );
  for (const momento of MOMENTOS) {
    const terraco = palco.querySelector<HTMLImageElement>(`.terraco-${momento}`);
    const url = c.urlSprite(c.fundo, momento, variante);
    if (terraco && !terraco.src.endsWith(url)) {
      terraco.src = url;
    }
  }
  return variante;
}

export interface OpcoesCena {
  palco: HTMLElement;
  camadaPecas: HTMLElement;
  camadaLugares: HTMLElement;
  cenario: CenarioFront;
  aoEscolherPeca: (peca: string, minha: boolean) => void;
  aoEscolherLugar: (lugar: string, bloqueio: string | null) => void;
}

/** O que a sessão marca na cena além da visão: a peça selecionada, a que você está sugerindo mover e as peças em destaque. */
export interface MarcasDaCena {
  readonly selecionada: string | null;
  readonly sugerindo: string | null;
  readonly destaques: readonly string[];
}

export interface CenaImperativa {
  desenhar(visao: Visao, marcas: MarcasDaCena): void;
  /** Terraço vazio: entrada, convite ou encerrado. */
  limpar(): void;
  /** Foca o primeiro lugar livre; diz se havia algum. */
  focarPrimeiroLugar(): boolean;
}

type PecaColocada = VisaoPeca & { lugar: string };

const colocada = (p: VisaoPeca): p is PecaColocada => p.lugar !== null;

export function criarCena({
  palco,
  camadaPecas,
  camadaLugares,
  cenario,
  aoEscolherPeca,
  aoEscolherLugar,
}: OpcoesCena): CenaImperativa {
  const { geometria } = cenario;
  const { largura, altura } = geometria;
  const diametroLugar = geometria.pxPorMetro * 0.95;
  // Fundo e sprites na mesma família. O <img> do fundo nasce sem src, para não baixar duas larguras:
  // a família sai do palco já medido.
  const variante = mostrarTerraco(palco, cenario);
  // "peça@lugar" em destaque no último desenho: a mesma peça movida de novo volta a pulsar.
  let destaquesDesenhados = new Set<string>();

  // Um lugar ou sprite fora do cena.json é contrato quebrado, não um caso a tratar.
  function pontoDe(lugar: string): Ponto {
    const ponto = geometria.lugares[lugar];
    if (ponto === undefined) {
      throw new Error(`Lugar sem posição no cena.json: ${lugar}`);
    }
    return ponto;
  }

  function medidasDe(nome: string): Record<Momento, MedidasSprite> {
    const medidas = geometria.props[nome];
    if (medidas === undefined) {
      throw new Error(`Sprite sem medidas no cena.json: ${nome}`);
    }
    return medidas;
  }

  function desenharPecas(visao: Visao, { selecionada, destaques }: MarcasDaCena): void {
    const fragmento = document.createDocumentFragment();
    const colocadas = visao.pecas.filter(colocada);
    colocadas.sort((a, b) => pontoDe(a.lugar).y - pontoDe(b.lugar).y);

    for (const p of colocadas) {
      const nome = cenario.sprite(p.peca, p.lugar);
      const { crepusculo: medidas, noite } = medidasDe(nome);
      const ponto = pontoDe(p.lugar);
      const minha = p.assento === visao.voce.assento;
      const dono = minha ? visao.voce.nome : (visao.parceiro?.nome ?? "");

      // Efeitos da revelação: o piso fica atrás do botão da peça; a frente, na frente.
      const efeitos = cenario.revelacao(p.peca).flatMap((e) => criarElementosDoEfeito(e, p.peca, ponto, geometria));
      const naCamada = (camada: CamadaDoEfeito) => efeitos.filter((e) => e.camada === camada).map((e) => e.el);
      fragmento.append(...naCamada("piso"));

      const botao = document.createElement("button");
      botao.type = "button";
      botao.className = "peca";
      botao.dataset.peca = p.peca;
      botao.dataset.dono = minha ? "voce" : "parceiro";
      botao.style.left = pct(ponto.x - medidas.ancoraX, largura);
      botao.style.top = pct(ponto.y - medidas.ancoraY, altura);
      botao.style.width = pct(medidas.largura, largura);
      const sinalizada = visao.sinal?.peca === p.peca;
      const emDestaque = destaques.includes(p.peca);
      // O texto visível (selo "Você", selo do sinal, selo "Mudou") entra no nome, depois do prefixo
      // "<peça> de <dono>, <lugar>"; o destaque fecha o nome.
      const complementos = [
        minha ? "Você colocou" : null,
        sinalizada ? "Sugerido mover" : null,
        minha ? null : "Ative para sugerir que mova",
        emDestaque ? "Mudou agora" : null,
      ].filter((c): c is string => c !== null);
      botao.setAttribute(
        "aria-label",
        [`${cenario.peca(p.peca).nome} de ${dono}, ${cenario.lugar(p.lugar).nome}`, ...complementos].join(". "),
      );
      if (minha) {
        botao.setAttribute("aria-pressed", String(selecionada === p.peca));
      }
      if (sinalizada) {
        botao.dataset.sinalizada = "true";
      }
      if (emDestaque) {
        botao.dataset.destaque = "true";
      }

      const img = new Image();
      img.src = cenario.urlSprite(nome, "crepusculo", variante);
      img.alt = "";
      img.className = "sprite-crepusculo";
      botao.append(img);

      // O sprite da noite tem outro recorte (sombra em outra direção): alinhar pela âncora.
      const imgNoite = new Image();
      imgNoite.src = cenario.urlSprite(nome, "noite", variante);
      imgNoite.alt = "";
      imgNoite.loading = "lazy";
      imgNoite.className = "sprite-noite";
      imgNoite.style.left = pct(medidas.ancoraX - noite.ancoraX, medidas.largura);
      imgNoite.style.top = pct(medidas.ancoraY - noite.ancoraY, medidas.altura);
      imgNoite.style.width = pct(noite.largura, medidas.largura);
      botao.append(imgNoite);

      const selo = document.createElement("span");
      selo.className = "selo";
      selo.textContent = minha ? "Você" : dono;
      selo.style.left = pct(medidas.ancoraX, medidas.largura);
      selo.style.top = pct(medidas.ancoraY + 10, medidas.altura);
      selo.setAttribute("aria-hidden", "true");
      botao.append(selo);

      if (sinalizada) {
        // Indicador estático: continua visível com movimento reduzido, quando a animação some.
        const seloSinal = document.createElement("span");
        seloSinal.className = "selo selo-sinal";
        seloSinal.textContent = "Sugerido mover";
        seloSinal.style.left = pct(medidas.ancoraX, medidas.largura);
        seloSinal.setAttribute("aria-hidden", "true");
        botao.append(seloSinal);
      }

      if (emDestaque) {
        botao.append(...criarDestaque(medidas, !destaquesDesenhados.has(`${p.peca}@${p.lugar}`)));
      }

      botao.addEventListener("click", () => {
        aoEscolherPeca(p.peca, minha);
      });
      fragmento.append(botao, ...naCamada("frente"));
    }
    camadaPecas.replaceChildren(fragmento);
    destaquesDesenhados = new Set(
      colocadas.filter((p) => destaques.includes(p.peca)).map((p) => `${p.peca}@${p.lugar}`),
    );
  }

  /**
   * O anel no piso, na âncora da peça e do tamanho de um lugar, e o selo "Mudou" acima da peça. Os
   * dois são decorativos: o nome acessível já diz "Mudou agora". O anel pulsa só no primeiro desenho
   * do destaque; um redesenho no meio dele (outra seleção, outro destaque que acabou) o deixa parado.
   */
  function criarDestaque(medidas: MedidasSprite, pulsar: boolean): HTMLElement[] {
    const anel = document.createElement("span");
    anel.className = "anel-destaque";
    if (pulsar) {
      anel.dataset.pulsar = "true";
    }
    anel.style.left = pct(medidas.ancoraX, medidas.largura);
    anel.style.top = pct(medidas.ancoraY, medidas.altura);
    anel.style.width = pct(diametroLugar, medidas.largura);
    anel.setAttribute("aria-hidden", "true");

    const selo = document.createElement("span");
    selo.className = "selo selo-mudou";
    selo.textContent = "Mudou";
    selo.style.left = pct(medidas.ancoraX, medidas.largura);
    selo.setAttribute("aria-hidden", "true");
    return [anel, selo];
  }

  function desenharLugares(visao: Visao, selecionada: string | null): void {
    camadaLugares.replaceChildren();
    if (!selecionada) {
      return;
    }
    const minha = visao.pecas.find((p) => p.peca === selecionada);
    for (const id of minha?.lugaresPermitidos ?? []) {
      camadaLugares.append(criarLugar(id, selecionada, null));
    }
    // Lugares da mesma zona ocupados pelo parceiro aparecem bloqueados, com motivo.
    const zona = cenario.peca(selecionada).zona;
    for (const p of visao.pecas.filter(colocada)) {
      if (p.assento !== visao.voce.assento && cenario.peca(p.peca).zona === zona) {
        camadaLugares.append(criarLugar(p.lugar, selecionada, `ocupado por ${visao.parceiro?.nome ?? "outra pessoa"}`));
      }
    }
  }

  function criarLugar(id: string, peca: string, bloqueio: string | null): HTMLButtonElement {
    const ponto = pontoDe(id);
    const nomeLugar = cenario.lugar(id).nome;
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "lugar";
    botao.dataset.lugar = id;
    botao.style.left = pct(ponto.x, largura);
    botao.style.top = pct(ponto.y, altura);
    botao.style.width = pct(diametroLugar, largura);
    const rotulo = `Colocar ${cenario.peca(peca).artigo}: ${nomeLugar}`;
    if (bloqueio) {
      botao.setAttribute("aria-disabled", "true");
      botao.setAttribute("aria-label", `${nomeLugar}, ${bloqueio}`);
      botao.title = `Este lugar está ${bloqueio}`;
    } else {
      botao.setAttribute("aria-label", rotulo);
      const etiqueta = document.createElement("span");
      etiqueta.className = "rotulo-lugar";
      // Mesmo texto do nome acessível (WCAG 2.5.3): quem fala o que vê aciona o lugar.
      etiqueta.textContent = nomeLugar;
      etiqueta.setAttribute("aria-hidden", "true");
      botao.append(etiqueta);
    }
    botao.addEventListener("click", () => {
      aoEscolherLugar(id, bloqueio);
    });
    return botao;
  }

  return {
    desenhar(visao, marcas) {
      const { selecionada } = marcas;
      desenharPecas(visao, marcas);
      desenharLugares(visao, selecionada);
      palco.dataset.selecionando = selecionada ? "true" : "false";
    },
    limpar() {
      destaquesDesenhados = new Set();
      camadaPecas.replaceChildren();
      camadaLugares.replaceChildren();
      palco.dataset.selecionando = "false";
    },
    focarPrimeiroLugar() {
      const lugar = camadaLugares.querySelector<HTMLElement>(".lugar:not([aria-disabled])");
      lugar?.focus();
      return Boolean(lugar);
    },
  };
}
