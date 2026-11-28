// Service worker do Duora: guarda o shell para o app abrir sem rede e mostra os avisos de push. O
// cache nunca toca na API; a única chamada à API é renovar a inscrição de push quando ela troca.
// O build (frontend/build/service-worker.ts) compila este arquivo como script clássico e o põe
// depois de duas linhas fixas: CACHE_VERSION, a impressão dos arquivos servidos (mudou um arquivo,
// muda a versão, e o activate apaga o cache antigo), e SHELL, a lista tirada do manifest do Vite.
// Sem skipWaiting: o worker antigo segue no controle até todas as abas fecharem.
import {
  corpoDaInscricao,
  janelaParaFocar,
  MENSAGEM_DE_CAPACIDADES,
  notificacaoDoPush,
  urlSegura,
} from "./notificacao";
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

// H4: o payload traz só o tipo; título, corpo, tag e destino são fixos (sw/notificacao.ts).
self.addEventListener("push", (evento) => {
  const { titulo, opcoes } = notificacaoDoPush(evento.data?.text() ?? null);
  evento.waitUntil(self.registration.showNotification(titulo, opcoes));
});

self.addEventListener("notificationclick", (evento) => {
  evento.notification.close();
  const dados: unknown = evento.notification.data;
  const destino = urlSegura(typeof dados === "object" && dados !== null && "url" in dados ? dados.url : null);
  evento.waitUntil(focarOuAbrir(destino));
});

// Uma aba do Duora já no destino ganha o foco; senão abre uma nova, só num caminho desta origem.
async function focarOuAbrir(destino: string): Promise<void> {
  const janelas = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
  const janela = janelaParaFocar(janelas, destino, self.location.origin);
  if (janela) {
    await janela.focus();
    return;
  }
  await self.clients.openWindow(destino);
}

self.addEventListener("pushsubscriptionchange", (evento) => {
  evento.waitUntil(renovarInscricao(evento.oldSubscription, evento.newSubscription));
});

// Grava a inscrição nova (ou assina de novo com a chave da antiga). Sem sessão ou sem rede a falha
// é ignorada: a página renova a inscrição na próxima vez que ativar os avisos.
async function renovarInscricao(antiga: PushSubscription | null, nova: PushSubscription | null): Promise<void> {
  try {
    const inscricao =
      nova ??
      (antiga
        ? await self.registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: antiga.options.applicationServerKey,
          })
        : null);
    const corpo = inscricao ? corpoDaInscricao(inscricao.toJSON()) : null;
    if (!corpo) {
      return;
    }
    await fetch("/api/push/inscricao", {
      method: "PUT",
      headers: { "Content-Type": "application/json", "X-Duora": "1" },
      body: JSON.stringify(corpo),
      credentials: "same-origin",
    });
  } catch {
    // Falha ignorada (ver acima).
  }
}

// A página pergunta se este worker já trata push: um worker antigo no controle não responde.
self.addEventListener("message", (evento) => {
  if (evento.data === MENSAGEM_DE_CAPACIDADES) {
    evento.ports[0]?.postMessage({ push: true });
  }
});
