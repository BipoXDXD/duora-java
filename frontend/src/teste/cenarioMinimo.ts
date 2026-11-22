import { lerCenario } from "../cenario/ler";
import type { CenarioFront } from "../cenario/tipos";

const medidas = { largura: 200, altura: 160, ancoraX: 100, ancoraY: 140 };

/** Cenário de duas peças e dois lugares, sem sprites especiais nem revelação: prova que nada é do terraço. */
export const CENARIO_MINIMO: CenarioFront = lerCenario(
  {
    id: "minimo",
    nome: "Mínimo",
    fundo: "chao",
    zonas: {
      unica: { descricao: "Vai em qualquer lugar", instrucao: "em qualquer lugar", checklist: "{nome} na cena" },
    },
    lugares: {
      P1: { nome: "primeiro lugar" },
      P2: { nome: "segundo lugar" },
    },
    pecas: {
      pedra: { nome: "Pedra", artigo: "a pedra", assento: "A", zona: "unica", obrigatoria: true },
      folha: { nome: "Folha", artigo: "a folha", assento: "B", zona: "unica", obrigatoria: true },
    },
    sons: {
      ambiente: { volume: 0.35 },
      meteoro: { volume: 0.5 },
      revelacao: { volume: 0.6 },
    },
    legenda: { titulo: "Juntos: {juntos}", texto: "{nomeA} e {nomeB} terminaram." },
  },
  {
    largura: 1800,
    altura: 1186,
    pxPorMetro: 200,
    variantes: [900, 1800, 2700],
    lugares: {
      P1: { x: 600, y: 700 },
      P2: { x: 1200, y: 700 },
    },
    props: {
      pedra: { crepusculo: medidas, noite: medidas },
      folha: { crepusculo: medidas, noite: medidas },
    },
  },
  "minimo",
);
