// Service worker do Duora: guarda o shell para o app abrir sem rede. Nunca toca na API.
// O build (frontend/build/service-worker.ts) compila este arquivo como script clássico e o põe
// depois de duas linhas fixas: CACHE_VERSION, a impressão dos arquivos servidos (mudou um arquivo,
// muda a versão, e o activate apaga o cache antigo), e SHELL, a lista tirada do manifest do Vite.
// Sem skipWaiting: o worker antigo segue no controle até todas as abas fecharem.
import { rotaDoPedido } from "./rotas";

declare const self: ServiceWorkerGlobalScope;
declare const CACHE_VERSION: string;
declare const SHELL: string[];

self.addEventListener("install", (evento) => {
  // cache "reload": o shell novo vem do servidor, nunca de uma cópia do cache HTTP do navegador.
  evento.waitUntil(
    caches.open(CACHE_VERSION).then((cache) => cache.addAll(SHELL.map((url) => new Request(url, { cache: "reload" })))),
  );
});

self.addEventListener("activate", (evento) => {
  evento.waitUntil(
    caches
      .keys()
      .then((nomes) =>
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
  const rota = rotaDoPedido({
    url: new URL(pedido.url),
    origem: self.location.origin,
    metodo: pedido.method,
    modo: pedido.mode,
    temRange: pedido.headers.has("range"),
  });
  switch (rota) {
    case "ignorar":
      // Sem respondWith, o navegador segue sozinho.
      return;
    case "navegacao":
      // Sem rede e sem o shell guardado, um erro de rede explícito em vez de undefined.
      evento.respondWith(fetch(pedido).catch(() => caches.match("/").then((guardada) => guardada ?? Response.error())));
      return;
    case "rede-guardando":
      evento.respondWith(primeiroDaRede(pedido, { guardar: true }));
      return;
    case "cache-primeiro":
      evento.respondWith(primeiroDoCache(pedido));
      return;
    case "rede":
      evento.respondWith(primeiroDaRede(pedido, { guardar: false }));
      return;
  }
});

async function primeiroDoCache(pedido: Request): Promise<Response> {
  const guardada = await caches.match(pedido);
  if (guardada) {
    return guardada;
  }
  return guardarSeCompleta(pedido, await fetch(pedido));
}

// Com guardar, a resposta 200 da rede renova a cópia que serve de reserva sem rede.
async function primeiroDaRede(pedido: Request, { guardar }: { guardar: boolean }): Promise<Response> {
  let resposta: Response;
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

async function guardarSeCompleta(pedido: Request, resposta: Response): Promise<Response> {
  if (resposta.status === 200) {
    const cache = await caches.open(CACHE_VERSION);
    await cache.put(pedido, resposta.clone());
  }
  return resposta;
}
