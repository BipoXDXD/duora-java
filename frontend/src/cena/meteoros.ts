// Meteoros da revelação: alguns riscos de luz no lado do céu para onde o telescópio
// aponta, em sequência espaçada, e depois param. Com movimento reduzido, a mesma
// ideia vira poucos riscos parados.

const ESCURECIMENTO_MS = 2400;
const PRIMEIRO_APOS_MS = 600;
const INTERVALO_MIN_MS = 5000;
const INTERVALO_MAX_MS = 7000;

type Lado = "esquerdo" | "direito";

const LADOS: ReadonlySet<string> = new Set<Lado>(["esquerdo", "direito"]);

const ehLado = (valor: string | null | undefined): valor is Lado => valor != null && LADOS.has(valor);

/** Riscos parados (movimento reduzido): posição em % da camada e ângulo do lado esquerdo. */
const PARADOS = [
  { x: 58, y: 14, angulo: 152, escala: 1 },
  { x: 30, y: 30, angulo: 148, escala: 0.8 },
  { x: 74, y: 44, angulo: 156, escala: 0.7 },
  { x: 44, y: 62, angulo: 150, escala: 0.6 },
];

const entre = (min: number, max: number) => min + Math.random() * (max - min);

/** Ângulo do lado esquerdo espelhado para o direito: 150° (para baixo e à esquerda) vira 30°. */
const angulo = (graus: number, lado: Lado | null) => (lado === "direito" ? 180 - graus : graus);

export interface OpcoesMeteoros {
  aoMeteoro?: () => void;
  /** Lida a cada revelação: a preferência pode mudar com a página aberta. */
  movimentoReduzido: () => boolean;
}

export interface Meteoros {
  /** Idempotente: chamar de novo com a revelação já em curso não reinicia a sequência. Lado desconhecido é ignorado. */
  mostrar(lado: string | null | undefined): void;
  /** Refaz a revelação em curso no modo atual (a preferência de movimento mudou). */
  reiniciar(): void;
  limpar(): void;
}

interface Risco {
  x: number;
  y: number;
  graus: number;
  escala: number;
  parado?: boolean;
}

export function criarMeteoros(
  camada: HTMLElement,
  { aoMeteoro = () => {}, movimentoReduzido }: OpcoesMeteoros,
): Meteoros {
  let lado: Lado | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;

  function risco({ x, y, graus, escala, parado = false }: Risco): HTMLSpanElement {
    const el = document.createElement("span");
    el.className = parado ? "meteoro meteoro-parado" : "meteoro";
    el.style.left = `${x.toFixed(2)}%`;
    el.style.top = `${y.toFixed(2)}%`;
    el.style.setProperty("--angulo", `${angulo(graus, lado).toFixed(1)}deg`);
    el.style.setProperty("--escala", escala.toFixed(2));
    return el;
  }

  function lancar(restantes: number): void {
    // Começa longe da borda externa para atravessar a região do lado escolhido.
    const x = lado === "direito" ? entre(5, 55) : entre(45, 95);
    const el = risco({ x, y: entre(4, 46), graus: entre(144, 162), escala: entre(0.8, 1.2) });
    el.addEventListener(
      "animationend",
      () => {
        el.remove();
      },
      { once: true },
    );
    camada.append(el);
    aoMeteoro();
    if (restantes > 1) {
      timer = setTimeout(
        () => {
          lancar(restantes - 1);
        },
        entre(INTERVALO_MIN_MS, INTERVALO_MAX_MS),
      );
    } else {
      timer = undefined;
    }
  }

  function limpar(): void {
    clearTimeout(timer);
    timer = undefined;
    lado = null;
    delete camada.dataset.lado;
    camada.replaceChildren();
  }

  function mostrar(novoLado: string | null | undefined): void {
    if (!ehLado(novoLado) || lado === novoLado) {
      return;
    }
    limpar();
    lado = novoLado;
    camada.dataset.lado = lado;
    if (movimentoReduzido()) {
      camada.replaceChildren(
        ...PARADOS.map((p) => {
          const x = lado === "direito" ? 100 - p.x : p.x;
          return risco({ x, y: p.y, graus: p.angulo, escala: p.escala, parado: true });
        }),
      );
      return;
    }
    const total = 6 + Math.floor(Math.random() * 3);
    timer = setTimeout(() => {
      lancar(total);
    }, ESCURECIMENTO_MS + PRIMEIRO_APOS_MS);
  }

  return {
    mostrar,
    reiniciar() {
      const atual = lado;
      if (atual) {
        limpar();
        mostrar(atual);
      }
    },
    limpar,
  };
}
