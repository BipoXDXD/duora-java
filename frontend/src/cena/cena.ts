// Desenha a cena do cenário a partir da visão da sala. Não decide regras: usa os lugares
// permitidos que a visão traz para a peça selecionada e os destinos sugeríveis das do parceiro.

import type { AberturaDoCenario, CenarioFront, Efeito } from "../cenario/tipos";
import { escolherVariante, type MedidasSprite, type Momento, pct, type Ponto } from "../dominio/dados";
import type { Assento, Visao, VisaoAbertura, VisaoPeca } from "../dominio/visao";
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
  /** O toque na própria lanterna da abertura. */
  aoTocarAbertura: () => void;
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
  /** Foca o botão da peça na cena (não os efeitos dela, que também têm data-peca); diz se ela está lá. */
  focarPeca(peca: string): boolean;
}

type PecaColocada = VisaoPeca & { lugar: string };

/**
 * A luz de uma lanterna da abertura acesa, em metros da lanterna em tamanho real: o poço de luz da
 * revelação e a chama. A cena os desenha na escala da abertura.
 */
const LUZ_DA_LANTERNA: readonly Efeito[] = [
  { tipo: "brilho", cor: "#ffc982", largura: 2.4 },
  { tipo: "halo", alturaProjetada: 0.3464, largura: 0.75 },
];

const TREMOR_DA_CHAMA: Efeito = { tipo: "tremular" };

/** O anel da lanterna própria passa um pouco do sprite, para o dedo e para o olho. */
const FOLGA_DO_ANEL = 1.25;

const SVG = "http://www.w3.org/2000/svg";

/** Mão aberta sob uma chama: o gesto de acender, sem palavras. Decorativo (o botão tem nome). */
function criarIconeDeAcender(): SVGSVGElement {
  const svg = document.createElementNS(SVG, "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("aria-hidden", "true");
  svg.setAttribute("class", "icone-acender");
  const chama = document.createElementNS(SVG, "path");
  chama.setAttribute(
    "d",
    "M12 2.5c.7 2.3 3.4 3.8 3.4 6.9a3.4 3.4 0 0 1-6.8 0c0-1.5.8-2.5 1.6-3.4.3 1 .8 1.6 1.4 1.8-.3-1.8-.2-3.5.4-5.3z",
  );
  chama.setAttribute("fill", "currentColor");
  const mao = document.createElementNS(SVG, "path");
  mao.setAttribute(
    "d",
    "M3 15c1.6-.5 3 .1 4 1.1l1.2 1.2h5c.8 0 1.4.6 1.4 1.4H9.4M14.6 17.5l3.4-2.4c.8-.5 1.8-.3 2.2.4.4.6.3 1.4-.2 1.9l-3.6 3.2c-.7.6-1.6 1-2.5 1H8.5L6 20.2H3",
  );
  mao.setAttribute("fill", "none");
  mao.setAttribute("stroke", "currentColor");
  mao.setAttribute("stroke-width", "1.6");
  mao.setAttribute("stroke-linecap", "round");
  mao.setAttribute("stroke-linejoin", "round");
  svg.append(chama, mao);
  return svg;
}

const colocada = (p: VisaoPeca): p is PecaColocada => p.lugar !== null;

export function criarCena({
  palco,
  camadaPecas,
  camadaLugares,
  cenario,
  aoEscolherPeca,
  aoEscolherLugar,
  aoTocarAbertura,
}: OpcoesCena): CenaImperativa {
  const { geometria } = cenario;
  const { largura, altura } = geometria;
  const diametroLugar = geometria.pxPorMetro * 0.95;
  // Fundo e sprites na mesma família. O <img> do fundo nasce sem src, para não baixar duas larguras:
  // a família sai do palco já medido.
  const variante = mostrarTerraco(palco, cenario);
  // "peça@lugar" em destaque no último desenho: a mesma peça movida de novo volta a pulsar.
  let destaquesDesenhados = new Set<string>();
  // As lanternas acendem animadas só no primeiro desenho acesas: os redesenhos seguintes as mantêm.
  let lanternasAcesasDesenhadas = false;

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

  /**
   * Uma lanterna de mão por lado, atrás das peças. Na abertura ativa, a sua é o botão de acender e a
   * chama de cada lado tremula enquanto o dono dela tocou (o convite para o outro tocar também).
   * Acesas, as duas ficam com a luz da revelação em escala menor; apagadas pelo tempo, somem.
   */
  function desenharLanternas(visao: Visao): HTMLElement[] {
    const abertura = cenario.abertura();
    const estado = visao.abertura;
    if (abertura === null || estado === null || visao.fase !== "MONTANDO" || (!estado.ativa && !estado.acesa)) {
      lanternasAcesasDesenhadas = false;
      return [];
    }
    const acender = estado.acesa && !lanternasAcesasDesenhadas;
    lanternasAcesasDesenhadas = estado.acesa;
    const lados: readonly Assento[] = ["A", "B"];
    return lados.map((lado) => criarLanterna(abertura, estado, lado, lado === visao.voce.assento, acender));
  }

  function criarLanterna(
    abertura: AberturaDoCenario,
    estado: VisaoAbertura,
    lado: Assento,
    minha: boolean,
    acender: boolean,
  ): HTMLElement {
    const { escala } = abertura;
    const ponto = pontoDe(abertura.lugares[lado]);
    const { crepusculo: m } = medidasDe(abertura.sprite);
    // Os efeitos medem em metros: com o pxPorMetro na escala da lanterna, a luz encolhe junto.
    const emEscala = { ...geometria, pxPorMetro: geometria.pxPorMetro * escala };
    const tocou = estado.ativa && (minha ? estado.voceTocou : estado.outroTocou);
    const efeitos = [...(estado.acesa ? LUZ_DA_LANTERNA : []), ...(tocou ? [TREMOR_DA_CHAMA] : [])].flatMap((e) =>
      criarElementosDoEfeito(e, `abertura-${lado}`, ponto, emEscala),
    );

    const lanterna = document.createElement("div");
    lanterna.className = "lanterna";
    lanterna.dataset.lanterna = lado;
    lanterna.dataset.dono = minha ? "voce" : "parceiro";
    if (estado.acesa) {
      lanterna.dataset.acesa = "true";
    }
    if (acender) {
      lanterna.dataset.acender = "true";
    }

    const caixa = {
      x: ponto.x - m.ancoraX * escala,
      y: ponto.y - m.ancoraY * escala,
      largura: m.largura * escala,
      altura: m.altura * escala,
    };
    const img = new Image();
    img.src = cenario.urlSprite(abertura.sprite, "crepusculo", variante);
    img.alt = "";
    img.className = "sprite-lanterna";
    img.style.left = pct(caixa.x, largura);
    img.style.top = pct(caixa.y, altura);
    img.style.width = pct(caixa.largura, largura);

    lanterna.append(...efeitos.filter((e) => e.camada === "piso").map((e) => e.el), img);
    if (minha && estado.ativa) {
      lanterna.append(criarBotaoDaLanterna(caixa, tocou));
    }
    lanterna.append(...efeitos.filter((e) => e.camada === "frente").map((e) => e.el));
    return lanterna;
  }

  /** O anel de toque em volta da sua lanterna, com a mão e a chama; o nome diz o gesto. */
  function criarBotaoDaLanterna(
    caixa: { x: number; y: number; largura: number; altura: number },
    tocou: boolean,
  ): HTMLButtonElement {
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "botao-lanterna";
    botao.dataset.abertura = "";
    // O toque fica pressionado até a janela expirar: o leitor de tela ouve o mesmo que o anel sólido mostra.
    botao.setAttribute("aria-pressed", String(tocou));
    botao.setAttribute("aria-label", "Acender sua lanterna");
    botao.style.left = pct(caixa.x + caixa.largura / 2, largura);
    botao.style.top = pct(caixa.y + caixa.altura / 2, altura);
    botao.style.setProperty("--tamanho-lanterna", pct(Math.max(caixa.largura, caixa.altura) * FOLGA_DO_ANEL, largura));
    botao.append(criarIconeDeAcender());
    botao.addEventListener("click", () => {
      aoTocarAbertura();
    });
    return botao;
  }

  function desenharPecas(visao: Visao, { selecionada, sugerindo, destaques }: MarcasDaCena): void {
    const fragmento = document.createDocumentFragment();
    fragmento.append(...desenharLanternas(visao));
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
      // A peça do parceiro sem destino livre não aceita sugestão: fica indisponível, sem o convite.
      const sugerivel = !minha && (p.destinosSugeriveis?.length ?? 0) > 0;
      // O texto visível (selo "Você", selo do sinal, selo "Mudou") entra no nome, depois do prefixo
      // "<peça> de <dono>, <lugar>"; o destaque fecha o nome.
      const complementos = [
        minha ? "Você colocou" : null,
        sinalizada ? "Sugerido mover" : null,
        sugerivel ? "Ative para sugerir que mova" : null,
        emDestaque ? "Mudou agora" : null,
      ].filter((c): c is string => c !== null);
      botao.setAttribute(
        "aria-label",
        [`${cenario.peca(p.peca).nome} de ${dono}, ${cenario.lugar(p.lugar).nome}`, ...complementos].join(". "),
      );
      if (minha) {
        botao.setAttribute("aria-pressed", String(selecionada === p.peca));
      } else if (sugerivel) {
        botao.setAttribute("aria-pressed", String(sugerindo === p.peca));
      } else {
        botao.setAttribute("aria-disabled", "true");
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

  function desenharLugares(visao: Visao, { selecionada, sugerindo }: MarcasDaCena): void {
    camadaLugares.replaceChildren();
    const recebido = visao.sinal !== null && visao.sinal.deAssento !== visao.voce.assento ? visao.sinal : null;
    if (recebido) {
      // Antes dos botões: fica por baixo de um lugar livre no mesmo ponto.
      camadaLugares.append(criarLugarSugerido(recebido.lugar));
    }
    if (sugerindo) {
      const doParceiro = visao.pecas.find((p) => p.peca === sugerindo);
      for (const id of doParceiro?.destinosSugeriveis ?? []) {
        camadaLugares.append(criarSugestao(id, sugerindo));
      }
      return;
    }
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

  /** Posição e tamanho de um lugar no palco, iguais para botões e marcas. */
  function posicionarNoLugar(el: HTMLElement, id: string): void {
    const ponto = pontoDe(id);
    el.dataset.lugar = id;
    el.style.left = pct(ponto.x, largura);
    el.style.top = pct(ponto.y, altura);
    el.style.width = pct(diametroLugar, largura);
  }

  /**
   * O destino que o parceiro sugeriu, para quem recebe: decorativo (o nome da peça já diz
   * "Sugerido mover" e o inventário tem o "Mover …"). Contorno duplo, não só cor.
   */
  function criarLugarSugerido(id: string): HTMLElement {
    const marca = document.createElement("div");
    marca.className = "lugar-sugerido";
    marca.setAttribute("aria-hidden", "true");
    posicionarNoLugar(marca, id);
    return marca;
  }

  /** Um destino possível na sugestão: o rótulo "Que tal aqui?" fica visível, também no celular. */
  function criarSugestao(id: string, peca: string): HTMLButtonElement {
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "lugar";
    botao.dataset.sugestao = "true";
    posicionarNoLugar(botao, id);
    // O rótulo visível abre o nome (WCAG 2.5.3); o resto diz o que a sugestão leva ao parceiro.
    botao.setAttribute("aria-label", `Que tal aqui? Sugerir ${cenario.peca(peca).artigo}: ${cenario.lugar(id).nome}`);
    const etiqueta = document.createElement("span");
    etiqueta.className = "rotulo-lugar";
    etiqueta.textContent = "Que tal aqui?";
    etiqueta.setAttribute("aria-hidden", "true");
    botao.append(etiqueta);
    botao.addEventListener("click", () => {
      aoEscolherLugar(id, null);
    });
    return botao;
  }

  function criarLugar(id: string, peca: string, bloqueio: string | null): HTMLButtonElement {
    const nomeLugar = cenario.lugar(id).nome;
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "lugar";
    posicionarNoLugar(botao, id);
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
      const { selecionada, sugerindo } = marcas;
      desenharPecas(visao, marcas);
      desenharLugares(visao, marcas);
      palco.dataset.selecionando = selecionada ? "true" : "false";
      palco.dataset.sugerindo = sugerindo ? "true" : "false";
    },
    limpar() {
      destaquesDesenhados = new Set();
      lanternasAcesasDesenhadas = false;
      camadaPecas.replaceChildren();
      camadaLugares.replaceChildren();
      palco.dataset.selecionando = "false";
      palco.dataset.sugerindo = "false";
    },
    focarPrimeiroLugar() {
      const lugar = camadaLugares.querySelector<HTMLElement>(".lugar:not([aria-disabled])");
      lugar?.focus();
      return Boolean(lugar);
    },
    focarPeca(peca) {
      const botao = camadaPecas.querySelector<HTMLElement>(`button[data-peca="${peca}"]`);
      botao?.focus();
      return Boolean(botao);
    },
  };
}
