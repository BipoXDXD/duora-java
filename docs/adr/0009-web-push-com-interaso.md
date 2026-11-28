# 0009 — Web Push com interaso

- **Estado:** aceita
- **Data:** 2026-11-28
- **Etapa:** 4

## Contexto

Na Etapa 4, quem está na fila ou deixou a tela fechada precisa saber que "alguém compatível está esperando". O SSE (ADR 0002) só alcança uma aba aberta; o Web Push alcança o navegador fechado, pelo serviço de push do fabricante (FCM, Mozilla, Apple, Windows).

Enviar um Web Push pede três coisas: cifrar o payload para o navegador (RFC 8291, `aes128gcm`), assinar um JWT VAPID que identifica o servidor (RFC 8292) e fazer um `POST` ao endpoint da inscrição. O endpoint é uma URL que o navegador manda: se o servidor fizer o `POST` para qualquer URL recebida, vira um SSRF, capaz de bater em `169.254.169.254`, na rede interna ou em `localhost`. E a notificação aparece na tela bloqueada, onde qualquer pessoa ao lado vê o texto.

## Decisão

- **Biblioteca `com.interaso:webpush:1.3.0` (H5), só para cifra e VAPID.** Usamos `WebPush.getBody` (cifra) e `WebPush.getHeaders` (`Authorization` VAPID, `TTL`, `Topic`, `Urgency`, `Content-Encoding`). A única dependência transitiva é o `kotlin-stdlib`, na versão que o BOM do Spring Boot já gerencia.
- **O envio é do nosso `HttpClient`.** O `WebPushInterasoAdapter` faz o `POST` com o `HttpClient` do JDK: conexão em 5 s, requisição em 10 s e `followRedirects(NEVER)`, para um redirecionamento não levar o pedido para fora da allowlist. A resposta é descartada sem leitura. 2xx é `ENTREGUE`; 404 e 410 (o `EXPIRED` da interaso) são `EXPIRADA`, e a inscrição deve ser apagada; o resto, ou qualquer exceção, é `FALHOU`.
- **Allowlist contra SSRF.** `EnderecoDePush.validar` exige `https`, recusa userinfo, porta explícita e host literal (IPv4, IPv6 ou inteiro decimal), limita a URL a 1024 caracteres e aceita só um host de `duora.push.hosts-permitidos` (`fcm.googleapis.com`, `updates.push.services.mozilla.com`, `push.apple.com`, `notify.windows.com`) ou um subdomínio dele, com o ponto antes (`evilfcm.googleapis.com.example` não passa). O endpoint é validado ao inscrever e de novo antes de cada envio, porque a allowlist pode mudar entre um e outro.
- **Payload só com o tipo (H4).** O corpo cifrado é `{"tipo":"compativel"}`. Título, texto e destino do clique ficam fixos no service worker: nada pessoal sai do servidor nem aparece na tela bloqueada. O `Topic` (`duora-compativel`) faz um aviso novo substituir o que ainda não foi entregue, e o `TTL` de 1 hora descarta o aviso velho.
- **Desligado por padrão, inclusive em produção.** `duora.push.habilitado` nasce `false` (prod lê `DUORA_PUSH_HABILITADO`, padrão `false`), e o `EnvioDePushNoLog` só registra o id da inscrição. Ligado, as chaves VAPID (`DUORA_VAPID_PUBLICA`, `DUORA_VAPID_PRIVADA`) são obrigatórias e o boot cai sem elas. O par é gerado pelo teste manual `GerarChavesVapid`, que o imprime na saída e não grava arquivo.

## Consequências

- Os logs citam só o id da inscrição e o status ou a classe da exceção, nunca o endpoint nem as chaves.
- Como a interaso é Kotlin, ela lança exceções verificadas sem declará-las; o adapter captura `Exception` para nenhuma escapar.
- Um serviço de push fora da allowlist (outro navegador, outro fabricante) deixa a pessoa sem avisos até alguém acrescentar o host na propriedade.
- O `WebPushInterasoAdapterTest` prova o comportamento contra um `HttpServer` local: status, timeout, redirecionamento, cabeçalhos e o payload decifrado com a chave do navegador de teste.

## Alternativas consideradas

- **`nl.martijndwars:web-push`:** sem lançamento há anos, depende do Bouncy Castle e do Apache HttpClient (que já mantemos fora do classpath) e envia por conta própria, seguindo redirecionamentos. Rejeitada.
- **Implementação própria (RFC 8291/8292 com o JDK):** dá para fazer com `KeyAgreement`, HKDF sobre `Mac` e `AES/GCM`, mas é criptografia escrita à mão, com teste e manutenção nossos. A interaso faz só essa parte, é pequena e deixa o envio com a gente. Rejeitada.
