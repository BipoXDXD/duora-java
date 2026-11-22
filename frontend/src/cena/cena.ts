// Desenha a cena do cenário a partir da visão da sala. Não decide regras: usa os lugares
// permitidos que a visão traz para a peça selecionada.

import type { CenarioFront, Efeito } from "../cenario/tipos";
import { escolherVariante, type MedidasSprite, type Momento, type Ponto } from "../dominio/dados";
import type { Visao, VisaoPeca } from "../dominio/visao";

const pct = (valor: number, total: number) => `${((valor / total) * 100).toFixed(3)}%`;

const MOMENTOS: readonly Momento[] = ["crepusculo", "noite"];

/**
 * Escolhe a família pela largura do palco já medido × densidade da tela e aponta os dois
 * fundos do cenário para ela. Devolve a largura escolhida.
 */
export function mostrarTerraco(palco: HTMLElement, c: CenarioFront): number {
  const variante = escolherVariante(
    palco.getBoundingClientRect().width || window.innerWidth,
    window.devicePixelRatio || 1,
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

export interface CenaImperativa {
  desenhar(visao: Visao, selecionada: string | null): void;
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

  /** Efeito noturno da revelação, centrado no ponto da peça; só aparece à noite (cena.css). */
  function criarEfeito(efeito: Efeito, ponto: Ponto): HTMLDivElement {
    // Brilho: poço de luz no piso. Halo: luz sobre a peça (o modelo vem apagado), na altura já
    // projetada pela câmera inclinada.
    const brilho = efeito.tipo === "brilho-piso";
    const div = document.createElement("div");
    div.className = brilho ? "brilho" : "halo";
    div.style.left = pct(ponto.x, largura);
    div.style.top = pct(brilho ? ponto.y : ponto.y - geometria.pxPorMetro * efeito.alturaProjetada, altura);
    div.style.width = pct(geometria.pxPorMetro * efeito.largura, largura);
    return div;
  }

  function desenharPecas(visao: Visao, selecionada: string | null): void {
    const fragmento = document.createDocumentFragment();
    const colocadas = visao.pecas.filter(colocada);
    colocadas.sort((a, b) => pontoDe(a.lugar).y - pontoDe(b.lugar).y);

    for (const p of colocadas) {
      const nome = cenario.sprite(p.peca, p.lugar);
      const { crepusculo: medidas, noite } = medidasDe(nome);
      const ponto = pontoDe(p.lugar);
      const minha = p.assento === visao.voce.assento;
      const dono = minha ? visao.voce.nome : (visao.parceiro?.nome ?? "");

      // O brilho no piso fica atrás do botão da peça; o halo, na frente.
      const efeitos = cenario.revelacao(p.peca);
      const efeitosDe = (tipo: Efeito["tipo"]) =>
        efeitos.filter((e) => e.tipo === tipo).map((e) => criarEfeito(e, ponto));
      fragmento.append(...efeitosDe("brilho-piso"));

      const botao = document.createElement("button");
      botao.type = "button";
      botao.className = "peca";
      botao.dataset.peca = p.peca;
      botao.dataset.dono = minha ? "voce" : "parceiro";
      botao.style.left = pct(ponto.x - medidas.ancoraX, largura);
      botao.style.top = pct(ponto.y - medidas.ancoraY, altura);
      botao.style.width = pct(medidas.largura, largura);
      const sinalizada = visao.sinal?.peca === p.peca;
      // O texto visível (selo "Você", selo do sinal) entra no nome, depois do prefixo "<peça> de <dono>, <lugar>".
      const complementos = [
        minha ? "Você colocou" : null,
        sinalizada ? "Sugerido mover" : null,
        minha ? null : "Ative para sugerir que mova",
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

      botao.addEventListener("click", () => {
        aoEscolherPeca(p.peca, minha);
      });
      fragmento.append(botao, ...efeitosDe("halo"));
    }
    camadaPecas.replaceChildren(fragmento);
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
    desenhar(visao, selecionada) {
      desenharPecas(visao, selecionada);
      desenharLugares(visao, selecionada);
      palco.dataset.selecionando = selecionada ? "true" : "false";
    },
    limpar() {
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
