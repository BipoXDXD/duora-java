// Desenha o terraço a partir da visão da sala. Não decide regras: usa os lugares
// permitidos que a visão traz para a peça selecionada.

import { LUGARES, PECAS, sprite, urlSprite } from "./dados.js";

const pct = (valor, total) => `${((valor / total) * 100).toFixed(3)}%`;

export function criarCena({ palco, camadaPecas, camadaLugares, geometria, aoEscolherPeca, aoEscolherLugar }) {
  const { largura, altura } = geometria;
  const diametroLugar = geometria.pxPorMetro * 0.95;

  function desenharPecas(visao, selecionada) {
    const fragmento = document.createDocumentFragment();
    const colocadas = visao.pecas.filter((p) => p.lugar);
    colocadas.sort((a, b) => geometria.lugares[a.lugar].y - geometria.lugares[b.lugar].y);

    let haloPendente = null;
    for (const p of colocadas) {
      const nome = sprite(p.peca, p.lugar);
      const medidas = geometria.props[nome].crepusculo;
      const noite = geometria.props[nome].noite;
      const ponto = geometria.lugares[p.lugar];
      const minha = p.assento === visao.voce.assento;
      const dono = minha ? visao.voce.nome : visao.parceiro?.nome ?? "";

      if (p.peca === "lanterna") {
        // Poço de luz no piso: só aparece à noite.
        const brilho = document.createElement("div");
        brilho.className = "brilho";
        brilho.style.left = pct(ponto.x, largura);
        brilho.style.top = pct(ponto.y, altura);
        brilho.style.width = pct(geometria.pxPorMetro * 2.4, largura);
        fragmento.append(brilho);
        // Halo sobre o vidro: o modelo vem com a vela apagada e o vidro é textura opaca.
        // Altura da vela ~0,4 m projetada pela câmera inclinada 30° (cos 30° ≈ 0,866).
        const halo = document.createElement("div");
        halo.className = "halo";
        halo.style.left = pct(ponto.x, largura);
        halo.style.top = pct(ponto.y - geometria.pxPorMetro * 0.4 * 0.866, altura);
        halo.style.width = pct(geometria.pxPorMetro * 0.75, largura);
        haloPendente = halo;
      }

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
      ].filter(Boolean);
      botao.setAttribute(
        "aria-label",
        [`${PECAS[p.peca].nome} de ${dono}, ${LUGARES[p.lugar].nome}`, ...complementos].join(". "),
      );
      if (minha) {
        botao.setAttribute("aria-pressed", String(selecionada === p.peca));
      }
      if (sinalizada) {
        botao.dataset.sinalizada = "true";
      }

      const img = new Image();
      img.src = urlSprite(nome, "crepusculo");
      img.alt = "";
      img.className = "sprite-crepusculo";
      botao.append(img);

      // O sprite da noite tem outro recorte (sombra em outra direção): alinhar pela âncora.
      const imgNoite = new Image();
      imgNoite.src = urlSprite(nome, "noite");
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

      botao.addEventListener("click", () => aoEscolherPeca(p.peca, minha));
      fragmento.append(botao);
      if (haloPendente) {
        fragmento.append(haloPendente);
        haloPendente = null;
      }
    }
    camadaPecas.replaceChildren(fragmento);
  }

  function desenharLugares(visao, selecionada) {
    camadaLugares.replaceChildren();
    if (!selecionada) {
      return;
    }
    const minha = visao.pecas.find((p) => p.peca === selecionada);
    for (const id of minha?.lugaresPermitidos ?? []) {
      camadaLugares.append(criarLugar(id, selecionada, null));
    }
    // Lugares da mesma zona ocupados pelo parceiro aparecem bloqueados, com motivo.
    for (const p of visao.pecas) {
      if (p.lugar && p.assento !== visao.voce.assento && PECAS[p.peca].zona === PECAS[selecionada].zona) {
        camadaLugares.append(criarLugar(p.lugar, selecionada, `ocupado por ${visao.parceiro?.nome ?? "outra pessoa"}`));
      }
    }
  }

  function criarLugar(id, peca, bloqueio) {
    const ponto = geometria.lugares[id];
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "lugar";
    botao.dataset.lugar = id;
    botao.style.left = pct(ponto.x, largura);
    botao.style.top = pct(ponto.y, altura);
    botao.style.width = pct(diametroLugar, largura);
    const rotulo = `Colocar ${PECAS[peca].artigo}: ${LUGARES[id].nome}`;
    if (bloqueio) {
      botao.setAttribute("aria-disabled", "true");
      botao.setAttribute("aria-label", `${LUGARES[id].nome}, ${bloqueio}`);
      botao.title = `Este lugar está ${bloqueio}`;
    } else {
      botao.setAttribute("aria-label", rotulo);
      const etiqueta = document.createElement("span");
      etiqueta.className = "rotulo-lugar";
      // Mesmo texto do nome acessível (WCAG 2.5.3): quem fala o que vê aciona o lugar.
      etiqueta.textContent = LUGARES[id].nome;
      etiqueta.setAttribute("aria-hidden", "true");
      botao.append(etiqueta);
    }
    botao.addEventListener("click", () => aoEscolherLugar(id, bloqueio));
    return botao;
  }

  return {
    desenhar(visao, selecionada) {
      desenharPecas(visao, selecionada);
      desenharLugares(visao, selecionada);
      palco.dataset.selecionando = selecionada ? "true" : "false";
    },
    /** Terraço vazio: entrada, convite ou encerrado. */
    limpar() {
      camadaPecas.replaceChildren();
      camadaLugares.replaceChildren();
      palco.dataset.selecionando = "false";
    },
    /** Foca o primeiro lugar livre; diz se havia algum. */
    focarPrimeiroLugar() {
      const lugar = camadaLugares.querySelector(".lugar:not([aria-disabled])");
      lugar?.focus();
      return Boolean(lugar);
    },
  };
}

/** Estrelas determinísticas: a mesma distribuição em todas as telas. */
export function desenharCeu(svgEstrelas, janelas) {
  let semente = 7;
  const aleatorio = () => {
    semente = (semente * 16807) % 2147483647;
    return semente / 2147483647;
  };
  const ns = "http://www.w3.org/2000/svg";
  for (let i = 0; i < 90; i++) {
    const c = document.createElementNS(ns, "circle");
    c.setAttribute("cx", (aleatorio() * 1000).toFixed(1));
    c.setAttribute("cy", (aleatorio() ** 1.4 * 600).toFixed(1));
    c.setAttribute("r", (0.6 + aleatorio() ** 3 * 1.8).toFixed(2));
    c.setAttribute("opacity", (0.35 + aleatorio() * 0.65).toFixed(2));
    svgEstrelas.append(c);
  }
  for (let i = 0; i < 14; i++) {
    const s = document.createElement("span");
    s.style.left = `${(aleatorio() * 98).toFixed(2)}%`;
    s.style.top = `${(30 + aleatorio() * 55).toFixed(2)}%`;
    janelas.append(s);
  }
}

/** Liga o céu pintado só se as duas imagens carregarem; senão ficam gradientes e silhueta SVG. */
export function prepararCeuPintado(app) {
  const carregar = (url) =>
    new Promise((resolve, reject) => {
      const imagem = new Image();
      imagem.addEventListener("load", resolve, { once: true });
      imagem.addEventListener("error", reject, { once: true });
      imagem.src = url;
    });
  Promise.all(["/assets/ceu/ceu-crepusculo.webp", "/assets/ceu/ceu-noite.webp"].map(carregar))
    .then(() => app.classList.add("ceu-pintado"))
    .catch(() => {});
}
