// Service worker do Duora: guarda o shell para o app abrir sem rede. Nunca toca na API.
// Os binários de /assets/ vêm do cache primeiro; o JSON de /assets/ (o cena.json) vem da rede
// primeiro, porque sem skipWaiting o worker antigo segue no controle quando chega um JS novo, e o
// JS novo não pode ler dados velhos. A versão é a impressão dos arquivos de static/ (menos este):
// mudou um arquivo, o ServiceWorkerVersaoTest falha e mostra o valor novo; o activate apaga o
// cache antigo.
const CACHE_VERSION = "duora-436a539dd5e9";

const SHELL = [
  "/",
  "/index.html",
  "/manifest.webmanifest",
  "/css/app.css",
  "/js/api.js",
  "/js/cena.js",
  "/js/dados.js",
  "/js/main.js",
  "/js/mensagens.js",
  "/js/meteoros.js",
  "/js/painel.js",
  "/js/pwa.js",
  "/js/som.js",
  "/js/telas.js",
  "/assets/fonts/atkinson-next-400.woff2",
  "/assets/fonts/atkinson-next-600.woff2",
  "/assets/fonts/fraunces-600.woff2",
  "/assets/icone.svg",
  "/assets/icones/icone-192.png",
  "/assets/icones/icone-512.png",
  "/assets/icones/maskable-192.png",
  "/assets/icones/maskable-512.png",
  "/assets/icones/apple-touch-180.png",
];

self.addEventListener("install", (evento) => {
  // cache "reload": o shell novo vem do servidor, nunca de uma cópia do cache HTTP do navegador.
  evento.waitUntil(
    caches
      .open(CACHE_VERSION)
      .then((cache) => cache.addAll(SHELL.map((url) => new Request(url, { cache: "reload" })))),
  );
});

self.addEventListener("activate", (evento) => {
  evento.waitUntil(
    caches.keys().then((nomes) =>
      Promise.all(
        nomes
          .filter((nome) => nome.startsWith("duora-") && nome !== CACHE_VERSION)
          .map((nome) => caches.delete(nome)),
      ),
    ),
  );
});

self.addEventListener("fetch", (evento) => {
  const pedido = evento.request;
  const url = new URL(pedido.url);
  // Sem respondWith, o navegador segue sozinho: API, escrita, outra origem e pedidos parciais
  // (o áudio pede por faixa de bytes, e o Cache API não guarda resposta 206).
  if (
    pedido.method !== "GET" ||
    url.origin !== self.location.origin ||
    url.pathname.startsWith("/api/") ||
    pedido.headers.has("range")
  ) {
    return;
  }
  if (pedido.mode === "navigate") {
    // Sem rede e sem o shell guardado, um erro de rede explícito em vez de undefined.
    evento.respondWith(
      fetch(pedido).catch(() => caches.match("/").then((guardada) => guardada ?? Response.error())),
    );
  } else if (url.pathname.startsWith("/assets/") && url.pathname.endsWith(".json")) {
    evento.respondWith(primeiroDaRede(pedido, { guardar: true }));
  } else if (url.pathname.startsWith("/assets/")) {
    evento.respondWith(primeiroDoCache(pedido));
  } else {
    evento.respondWith(primeiroDaRede(pedido));
  }
});

async function primeiroDoCache(pedido) {
  const guardada = await caches.match(pedido);
  if (guardada) {
    return guardada;
  }
  return guardarSeCompleta(pedido, await fetch(pedido));
}

// Com guardar, a resposta 200 da rede renova a cópia que serve de reserva sem rede.
async function primeiroDaRede(pedido, { guardar = false } = {}) {
  let resposta;
  try {
    resposta = await fetch(pedido);
  } catch (falha) {
    const guardada = await caches.match(pedido);
    if (guardada) {
      return guardada;
    }
    throw falha;
  }
  return guardar ? guardarSeCompleta(pedido, resposta) : resposta;
}

async function guardarSeCompleta(pedido, resposta) {
  if (resposta.status === 200) {
    const cache = await caches.open(CACHE_VERSION);
    await cache.put(pedido, resposta.clone());
  }
  return resposta;
}
