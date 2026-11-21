// Som opcional: ambiente em loop baixo, um acorde quando o céu escurece e um toque a cada
// meteoro. Usa Web Audio (o loop de <audio> com MP3 deixa um silêncio a cada volta). Só
// baixa os arquivos quando a pessoa liga o som e falha em silêncio. Nada depende dele.

const ARQUIVOS = {
  ambiente: "/assets/audio/ambiente.mp3",
  meteoro: "/assets/audio/meteoro.mp3",
  revelacao: "/assets/audio/revelacao.mp3",
};
const VOLUMES = { ambiente: 0.35, meteoro: 0.5, revelacao: 0.6 };
const SUBIDA_S = 1.5;

async function carregar(contexto, url) {
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

export function criarSom() {
  let ligado = false;
  let contexto = null;
  let carregamento = null;
  const buffers = {};
  let ambiente = null;
  let ganhoAmbiente = null;

  function preparar() {
    if (!contexto) {
      const Contexto = window.AudioContext ?? window.webkitAudioContext;
      if (!Contexto) {
        return Promise.resolve();
      }
      contexto = new Contexto();
      const meu = contexto;
      carregamento = Promise.all(
        Object.entries(ARQUIVOS).map(async ([nome, url]) => {
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
    return carregamento;
  }

  function iniciarAmbiente() {
    if (ambiente || !buffers.ambiente) {
      return;
    }
    ganhoAmbiente = contexto.createGain();
    ganhoAmbiente.gain.setValueAtTime(0, contexto.currentTime);
    ganhoAmbiente.gain.linearRampToValueAtTime(VOLUMES.ambiente, contexto.currentTime + SUBIDA_S);
    ganhoAmbiente.connect(contexto.destination);
    ambiente = contexto.createBufferSource();
    ambiente.buffer = buffers.ambiente;
    ambiente.loop = true;
    ambiente.connect(ganhoAmbiente);
    ambiente.start();
  }

  function pararAmbiente() {
    ambiente?.stop();
    ambiente?.disconnect();
    ganhoAmbiente?.disconnect();
    ambiente = null;
    ganhoAmbiente = null;
  }

  function disparar(nome) {
    if (!ligado || !contexto) {
      return;
    }
    if (!buffers[nome]) {
      // Ainda carregando: toca quando chegar, se o som continuar ligado.
      carregamento?.then(() => buffers[nome] && disparar(nome));
      return;
    }
    const ganho = contexto.createGain();
    ganho.gain.value = VOLUMES[nome];
    ganho.connect(contexto.destination);
    const fonte = contexto.createBufferSource();
    fonte.buffer = buffers[nome];
    fonte.connect(ganho);
    fonte.addEventListener("ended", () => ganho.disconnect());
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
      pronto.then(() => {
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
