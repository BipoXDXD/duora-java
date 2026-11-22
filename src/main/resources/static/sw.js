// Service worker do Duora: guarda o shell para o app abrir sem rede. Nunca toca na API.
// Ao mudar o shell ou qualquer arquivo em /assets/, troque a versão: o activate apaga o cache antigo.
const CACHE_VERSION = "duora-v1";

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
  evento.waitUntil(caches.open(CACHE_VERSION).then((cache) => cache.addAll(SHELL)));
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
    evento.respondWith(fetch(pedido).catch(() => caches.match("/")));
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
  const resposta = await fetch(pedido);
  if (resposta.status === 200) {
    const cache = await caches.open(CACHE_VERSION);
    await cache.put(pedido, resposta.clone());
  }
  return resposta;
}

async function primeiroDaRede(pedido) {
  try {
    return await fetch(pedido);
  } catch (falha) {
    const guardada = await caches.match(pedido);
    if (guardada) {
      return guardada;
    }
    throw falha;
  }
}
