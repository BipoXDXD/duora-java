// Textos e arte de cada peça e lugar. As regras ficam no servidor; aqui só há apresentação.

export const PECAS = {
  telescopio: { nome: "Telescópio", artigo: "o telescópio", assento: "A", zona: "centro", obrigatoria: true },
  manta: { nome: "Manta", artigo: "a manta", assento: "A", zona: "lateral" },
  radio: { nome: "Rádio", artigo: "o rádio", assento: "A", zona: "lateral" },
  banco: { nome: "Banco", artigo: "o banco", assento: "B", zona: "centro", obrigatoria: true },
  lanterna: { nome: "Lanterna", artigo: "a lanterna", assento: "B", zona: "lateral" },
  garrafa: { nome: "Garrafa térmica", artigo: "a garrafa térmica", assento: "B", zona: "lateral" },
};

export const LUGARES = {
  C1: { nome: "centro, à esquerda" },
  C2: { nome: "centro, à direita" },
  L1: { nome: "fundo, à esquerda" },
  L2: { nome: "frente, à esquerda" },
  R1: { nome: "fundo, à direita" },
  R2: { nome: "frente, à direita" },
};

/** Nome do sprite: o telescópio aponta para o lado do céu do seu lugar. */
export function sprite(peca, lugar) {
  if (peca === "telescopio") {
    return lugar === "C2" ? "telescopio-dir" : "telescopio-esq";
  }
  return peca;
}

/**
 * Largura do render (família do terraço) para a tela: a menor variante que cobre
 * larguraCss × dpr, ou a maior quando nenhuma cobre.
 */
export function escolherVariante(larguraCss, dpr, variantes) {
  const ordenadas = [...variantes].sort((a, b) => a - b);
  const alvo = larguraCss * dpr;
  return ordenadas.find((v) => v >= alvo) ?? ordenadas[ordenadas.length - 1];
}

/** Miniaturas do inventário têm 56 px: a família menor basta em qualquer densidade. */
export const LARGURA_MINIATURA = 900;

export function urlSprite(nome, momento, largura) {
  return `/assets/cena/${nome}-${momento}-${largura}.webp`;
}

export async function carregarCena() {
  const resposta = await fetch("/assets/cena/cena.json");
  if (!resposta.ok) {
    throw new Error(`cena.json indisponível (${resposta.status})`);
  }
  return resposta.json();
}
