// Único ponto do front que conhece o formato do cenario.json e do cena.json (e o único que importa o
// valibot, guardado pelo ESLint). Valida os dois, confere um contra o outro e entrega o CenarioFront.

import * as v from "valibot";
import type { Geometria } from "../dominio/dados";
import { ErroCenario, type CenarioFront } from "./tipos";

const Medidas = v.strictObject({ largura: v.number(), altura: v.number(), ancoraX: v.number(), ancoraY: v.number() });

const EsquemaGeometria = v.strictObject({
  largura: v.number(),
  altura: v.number(),
  pxPorMetro: v.number(),
  variantes: v.array(v.number()),
  lugares: v.record(v.string(), v.strictObject({ x: v.number(), y: v.number() })),
  props: v.record(v.string(), v.strictObject({ crepusculo: Medidas, noite: Medidas })),
});

const Som = v.strictObject({ volume: v.pipe(v.number(), v.minValue(0), v.maxValue(1)) });

const Efeito = v.variant("tipo", [
  v.strictObject({ tipo: v.literal("brilho-piso"), largura: v.number() }),
  v.strictObject({ tipo: v.literal("halo"), alturaProjetada: v.number(), largura: v.number() }),
]);

const EsquemaCenario = v.strictObject({
  id: v.string(),
  nome: v.string(),
  fundo: v.string(),
  zonas: v.record(v.string(), v.strictObject({ descricao: v.string(), instrucao: v.string(), checklist: v.string() })),
  lugares: v.record(v.string(), v.strictObject({ nome: v.string() })),
  pecas: v.record(
    v.string(),
    v.strictObject({
      nome: v.string(),
      artigo: v.string(),
      assento: v.picklist(["A", "B"]),
      zona: v.string(),
      obrigatoria: v.boolean(),
    }),
  ),
  sprites: v.optional(
    v.record(
      v.string(),
      v.strictObject({
        padrao: v.string(),
        porLugar: v.optional(v.record(v.string(), v.string()), {}),
        semLugar: v.optional(v.string()),
      }),
    ),
    {},
  ),
  revelacao: v.optional(v.record(v.string(), v.array(Efeito)), {}),
  sons: v.strictObject({ ambiente: Som, meteoro: Som, revelacao: Som }),
  legenda: v.strictObject({ titulo: v.string(), texto: v.string() }),
});

type CenarioLido = v.InferOutput<typeof EsquemaCenario>;
type SpriteLido = CenarioLido["sprites"][string];

/**
 * Lê e confere o cenário `id` a partir do cenario.json e do cena.json já decodificados.
 * Qualquer problema vira ErroCenario com o caminho pontuado do primeiro deles.
 */
export function lerCenario(cenario: unknown, geometria: unknown, id: string): CenarioFront {
  const c = validar(EsquemaCenario, cenario, "cenario.json");
  const g: Geometria = validar(EsquemaGeometria, geometria, "cena.json");
  conferir(c, g, id);

  return Object.freeze({
    id: c.id,
    nome: c.nome,
    geometria: g,
    fundo: c.fundo,
    peca(peca: string) {
      const dados = entrada(c.pecas, peca);
      if (dados === undefined) {
        throw new Error(`Peça desconhecida: ${peca}`);
      }
      return dados;
    },
    lugar(lugar: string) {
      const dados = entrada(c.lugares, lugar);
      if (dados === undefined) {
        throw new Error(`Lugar desconhecido: ${lugar}`);
      }
      return dados;
    },
    zona(zona: string) {
      const dados = entrada(c.zonas, zona);
      if (dados === undefined) {
        throw new Error(`Zona desconhecida: ${zona}`);
      }
      return dados;
    },
    sprite(peca: string, lugar: string | null) {
      const s = entrada(c.sprites, peca);
      if (s === undefined) {
        return peca;
      }
      if (lugar === null) {
        return s.semLugar ?? s.padrao;
      }
      return entrada(s.porLugar, lugar) ?? s.padrao;
    },
    urlSprite(nome, momento, largura) {
      return `/assets/cenarios/${c.id}/cena/${nome}-${momento}-${String(largura)}.webp`;
    },
    urlCeu(momento, largura) {
      return `/assets/cenarios/${c.id}/ceu/ceu-${momento}-${String(largura)}.webp`;
    },
    som(nome) {
      return { url: `/assets/cenarios/${c.id}/audio/${nome}.mp3`, volume: c.sons[nome].volume };
    },
    revelacao(peca: string) {
      return entrada(c.revelacao, peca) ?? [];
    },
    legenda() {
      throw new Error("legenda: Task F3");
    },
  } satisfies CenarioFront);
}

function validar<E extends v.GenericSchema>(esquema: E, valor: unknown, arquivo: string): v.InferOutput<E> {
  const resultado = v.safeParse(esquema, valor);
  if (!resultado.success) {
    const [problema] = resultado.issues;
    throw new ErroCenario(`${arquivo} inválido em ${v.getDotPath(problema) ?? "(raiz)"}: ${problema.message}`);
  }
  return resultado.output;
}

/** Coerência entre as partes do cenario.json e dele com o cena.json. */
function conferir(c: CenarioLido, g: Geometria, id: string): void {
  if (c.id !== id) {
    falha("id", `esperado "${id}", veio "${c.id}"`);
  }
  for (const [peca, dados] of Object.entries(c.pecas)) {
    if (!Object.hasOwn(c.zonas, dados.zona)) {
      falha(`pecas.${peca}.zona`, `a zona "${dados.zona}" não está em zonas`);
    }
  }
  for (const [peca, s] of Object.entries(c.sprites)) {
    if (!Object.hasOwn(c.pecas, peca)) {
      falha(`sprites.${peca}`, "não é uma peça");
    }
    for (const lugar of Object.keys(s.porLugar)) {
      if (!Object.hasOwn(c.lugares, lugar)) {
        falha(`sprites.${peca}.porLugar.${lugar}`, "não é um lugar");
      }
    }
  }
  for (const peca of Object.keys(c.revelacao)) {
    if (!Object.hasOwn(c.pecas, peca)) {
      falha(`revelacao.${peca}`, "não é uma peça");
    }
  }
  for (const lugar of Object.keys(c.lugares)) {
    if (!Object.hasOwn(g.lugares, lugar)) {
      falha(`lugares.${lugar}`, `sem ponto no cena.json (lugares.${lugar})`);
    }
  }
  for (const peca of Object.keys(c.pecas)) {
    for (const [caminho, nome] of nomesDeSprite(peca, entrada(c.sprites, peca))) {
      if (!Object.hasOwn(g.props, nome)) {
        falha(caminho, `o sprite "${nome}" não tem medidas no cena.json (props.${nome})`);
      }
    }
  }
}

/** Cada nome de sprite que a peça pode usar, com o caminho de onde veio. */
function nomesDeSprite(peca: string, s: SpriteLido | undefined): [caminho: string, nome: string][] {
  if (s === undefined) {
    return [[`pecas.${peca}`, peca]];
  }
  const nomes: [string, string][] = [[`sprites.${peca}.padrao`, s.padrao]];
  for (const [lugar, nome] of Object.entries(s.porLugar)) {
    nomes.push([`sprites.${peca}.porLugar.${lugar}`, nome]);
  }
  if (s.semLugar !== undefined) {
    nomes.push([`sprites.${peca}.semLugar`, s.semLugar]);
  }
  return nomes;
}

function falha(caminho: string, motivo: string): never {
  throw new ErroCenario(`cenario.json inválido em ${caminho}: ${motivo}`);
}

/** Só chaves próprias: "toString" ou "__proto__" não são peças. */
function entrada<T>(registro: Readonly<Record<string, T>>, chave: string): T | undefined {
  return Object.hasOwn(registro, chave) ? registro[chave] : undefined;
}
