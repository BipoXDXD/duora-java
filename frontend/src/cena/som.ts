// Som opcional: ambiente em loop baixo, um acorde quando o céu escurece e um toque a cada
// meteoro. Usa Web Audio (o loop de <audio> com MP3 deixa um silêncio a cada volta). Só
// baixa os arquivos quando a pessoa liga o som e falha em silêncio. Nada depende dele.

const ARQUIVOS = {
  ambiente: "/assets/cenarios/terraco/audio/ambiente.mp3",
  meteoro: "/assets/cenarios/terraco/audio/meteoro.mp3",
  revelacao: "/assets/cenarios/terraco/audio/revelacao.mp3",
};

type NomeSom = keyof typeof ARQUIVOS;

const VOLUMES: Record<NomeSom, number> = { ambiente: 0.35, meteoro: 0.5, revelacao: 0.6 };
const SUBIDA_S = 1.5;

/** O Safari antigo só expõe o construtor com prefixo. */
type JanelaComAudio = Window & { webkitAudioContext?: typeof AudioContext };

export interface Som {
  definir(ligado: boolean): void;
  meteoro(): void;
  revelacao(): void;
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

export function criarSom(): Som {
  let ligado = false;
  let contexto: AudioContext | null = null;
  let carregamento: Promise<void> | null = null;
  const buffers: Partial<Record<NomeSom, AudioBuffer | null>> = {};
  let ambiente: AudioBufferSourceNode | null = null;
  let ganhoAmbiente: GainNode | null = null;

  function preparar(): Promise<void> {
    if (!contexto) {
      const janela: JanelaComAudio = window;
      // O tipo do DOM diz que AudioContext sempre existe; navegadores antigos discordam.
      // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
      const Contexto = window.AudioContext ?? janela.webkitAudioContext;
      // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
      if (!Contexto) {
        return Promise.resolve();
      }
      contexto = new Contexto();
      const meu = contexto;
      carregamento = Promise.all(
        (Object.entries(ARQUIVOS) as [NomeSom, string][]).map(async ([nome, url]) => {
          buffers[nome] = await carregar(meu, url);
        }),
      ).then(() => {
        // Nada carregou (rede, servidor fora): descarta para tentar de novo no próximo clique.
        if (Object.values(buffers).every((b) => !b) && contexto === meu) {
          meu.close().catch(() => {});
          contexto = null;
          carregamento = null;
        }
      });
    }
    return carregamento ?? Promise.resolve();
  }

  function iniciarAmbiente(): void {
    const buffer = buffers.ambiente;
    if (ambiente || !buffer || !contexto) {
      return;
    }
    ganhoAmbiente = contexto.createGain();
    ganhoAmbiente.gain.setValueAtTime(0, contexto.currentTime);
    ganhoAmbiente.gain.linearRampToValueAtTime(VOLUMES.ambiente, contexto.currentTime + SUBIDA_S);
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
    const ganho = contexto.createGain();
    ganho.gain.value = VOLUMES[nome];
    ganho.connect(contexto.destination);
    const fonte = contexto.createBufferSource();
    fonte.buffer = buffer;
    fonte.connect(ganho);
    fonte.addEventListener("ended", () => {
      ganho.disconnect();
    });
    fonte.start();
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
      const pronto = preparar();
      contexto?.resume().catch(() => {});
      void pronto.then(() => {
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
  };
}
