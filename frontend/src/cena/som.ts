// Som opcional: ambiente em loop baixo, um acorde quando o céu escurece e um toque a cada
// meteoro. Usa Web Audio (o loop de <audio> com MP3 deixa um silêncio a cada volta). Só
// baixa os arquivos quando a pessoa liga o som e falha em silêncio. Nada depende dele.
// Exceção: as notas do rádio tocam mesmo com o som desligado (o chamador decide quando).

import type { CenarioFront, NomeSom } from "../cenario/tipos";

const NOMES: readonly NomeSom[] = ["ambiente", "meteoro", "revelacao"];
const SUBIDA_S = 1.5;

/** URL e volume de cada som, como o cenário define. */
export function arquivosDoSom(c: CenarioFront): Record<NomeSom, { url: string; volume: number }> {
  return { ambiente: c.som("ambiente"), meteoro: c.som("meteoro"), revelacao: c.som("revelacao") };
}

/** O Safari antigo só expõe o construtor com prefixo. */
type JanelaComAudio = Window & { webkitAudioContext?: typeof AudioContext };

export interface Som {
  definir(ligado: boolean): void;
  meteoro(): void;
  revelacao(): void;
  /** Toca uma vez o som da URL, mesmo com o som desligado; falha em silêncio. */
  notas(url: string, volume: number): void;
  /** Cria e retoma o contexto (dentro de um gesto), sem baixar nada. */
  desbloquear(): void;
}

async function carregar(contexto: AudioContext, url: string): Promise<AudioBuffer | null> {
  try {
    const resposta = await fetch(url);
    if (!resposta.ok) {
      return null;
    }
    return await contexto.decodeAudioData(await resposta.arrayBuffer());
  } catch {
    return null;
  }
}

export function criarSom(c: CenarioFront): Som {
  const arquivos = arquivosDoSom(c);
  let ligado = false;
  let contexto: AudioContext | null = null;
  let carregamento: Promise<void> | null = null;
  const buffers: Partial<Record<NomeSom, AudioBuffer | null>> = {};
  let ambiente: AudioBufferSourceNode | null = null;
  let ganhoAmbiente: GainNode | null = null;

  /** Cria o contexto na primeira vez e o retoma; null se o navegador não tem Web Audio. */
  function desbloquear(): AudioContext | null {
    if (!contexto) {
      const janela: JanelaComAudio = window;
      // O tipo do DOM diz que AudioContext sempre existe; navegadores antigos discordam.
      // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
      const Contexto = window.AudioContext ?? janela.webkitAudioContext;
      // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
      if (!Contexto) {
        return null;
      }
      contexto = new Contexto();
    }
    contexto.resume().catch(() => {});
    return contexto;
  }

  function preparar(meu: AudioContext): Promise<void> {
    carregamento ??= Promise.all(
      NOMES.map(async (nome) => {
        buffers[nome] = await carregar(meu, arquivos[nome].url);
      }),
    ).then(() => {
      // Nada carregou (rede, servidor fora): esquece para tentar de novo no próximo clique.
      if (Object.values(buffers).every((b) => !b)) {
        carregamento = null;
      }
    });
    return carregamento;
  }

  function tocar(meu: AudioContext, buffer: AudioBuffer, volume: number): void {
    const ganho = meu.createGain();
    ganho.gain.value = volume;
    ganho.connect(meu.destination);
    const fonte = meu.createBufferSource();
    fonte.buffer = buffer;
    fonte.connect(ganho);
    fonte.addEventListener("ended", () => {
      ganho.disconnect();
    });
    fonte.start();
  }

  function iniciarAmbiente(): void {
    const buffer = buffers.ambiente;
    if (ambiente || !buffer || !contexto) {
      return;
    }
    ganhoAmbiente = contexto.createGain();
    ganhoAmbiente.gain.setValueAtTime(0, contexto.currentTime);
    ganhoAmbiente.gain.linearRampToValueAtTime(arquivos.ambiente.volume, contexto.currentTime + SUBIDA_S);
    ganhoAmbiente.connect(contexto.destination);
    ambiente = contexto.createBufferSource();
    ambiente.buffer = buffer;
    ambiente.loop = true;
    ambiente.connect(ganhoAmbiente);
    ambiente.start();
  }

  function pararAmbiente(): void {
    ambiente?.stop();
    ambiente?.disconnect();
    ganhoAmbiente?.disconnect();
    ambiente = null;
    ganhoAmbiente = null;
  }

  function disparar(nome: NomeSom): void {
    if (!ligado || !contexto) {
      return;
    }
    const buffer = buffers[nome];
    if (!buffer) {
      // Ainda carregando: toca quando chegar, se o som continuar ligado.
      void carregamento?.then(() => {
        if (buffers[nome]) {
          disparar(nome);
        }
      });
      return;
    }
    tocar(contexto, buffer, arquivos[nome].volume);
  }

  return {
    definir(valor) {
      ligado = valor;
      if (!ligado) {
        pararAmbiente();
        contexto?.suspend().catch(() => {});
        return;
      }
      // Criado e retomado dentro do clique, como os navegadores exigem.
      const meu = desbloquear();
      if (!meu) {
        return;
      }
      void preparar(meu).then(() => {
        if (ligado) {
          iniciarAmbiente();
        }
      });
    },
    meteoro() {
      disparar("meteoro");
    },
    revelacao() {
      disparar("revelacao");
    },
    notas(url, volume) {
      const meu = desbloquear();
      if (!meu) {
        return;
      }
      void carregar(meu, url)
        .then((buffer) => {
          if (buffer) {
            tocar(meu, buffer, volume);
          }
        })
        .catch(() => {});
    },
    desbloquear() {
      desbloquear();
    },
  };
}
