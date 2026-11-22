// Céu do terraço: estrelas e janelas determinísticas, e o céu pintado quando as imagens carregam.

const SVG = "http://www.w3.org/2000/svg";

/** Estrelas determinísticas: a mesma distribuição em todas as telas. */
export function desenharCeu(svgEstrelas: SVGSVGElement, janelas: HTMLElement): void {
  let semente = 7;
  const aleatorio = () => {
    semente = (semente * 16807) % 2147483647;
    return semente / 2147483647;
  };
  for (let i = 0; i < 90; i++) {
    const c = document.createElementNS(SVG, "circle");
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

function carregar(url: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const imagem = new Image();
    imagem.addEventListener(
      "load",
      () => {
        resolve();
      },
      { once: true },
    );
    imagem.addEventListener(
      "error",
      () => {
        reject(new Error(`Céu indisponível: ${url}`));
      },
      { once: true },
    );
    imagem.src = url;
  });
}

/**
 * Diz se o céu pintado pode ser ligado: só se as duas imagens carregarem; senão ficam gradientes
 * e silhueta SVG. Pré-carrega a mesma largura que o image-set do CSS escolhe (1x → 1280, 2x → 2560).
 * Não toca no DOM: quem chama põe a classe .ceu-pintado no .app.
 */
export async function prepararCeuPintado(): Promise<boolean> {
  const largura = (window.devicePixelRatio || 1) > 1 ? 2560 : 1280;
  try {
    await Promise.all(
      ["crepusculo", "noite"].map((momento) =>
        carregar(`/assets/cenarios/terraco/ceu/ceu-${momento}-${String(largura)}.webp`),
      ),
    );
    return true;
  } catch {
    return false;
  }
}
