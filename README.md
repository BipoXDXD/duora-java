# Duora: Antes da primeira estrela

Demonstração local para duas pessoas. Uma cria um terraço e envia o convite; a outra entra por
ele. Juntas, cada uma com as próprias peças (telescópio, manta e rádio de um lado; banco,
lanterna e garrafa térmica do outro), montam o terraço em seis lugares. Quando as duas confirmam
a mesma composição, anoitece e o céu é revelado com um resultado que depende do que cada uma
escolheu. Dá para voltar a ajustar e revelar de novo.

## Experimentar localmente

Pré-requisitos: JDK 25. O Maven vem pelo wrapper (`./mvnw`).

```sh
./mvnw spring-boot:run
```

1. Abra <http://127.0.0.1:8080> numa janela normal, escolha um apelido e crie o terraço.
2. Copie o link do convite e abra-o numa janela anônima (ou em outro navegador): cada janela
   precisa do próprio cookie para ser uma pessoa diferente.
3. Montem o terraço e confirmem as duas.

## Testes

```sh
./mvnw verify          # formatação (Spotless), compilação com -Werror, testes de regras, API e contraste
./mvnw -Pe2e verify    # inclui as jornadas no Chromium (Playwright), com capturas em target/e2e-capturas
```

Na primeira execução do perfil `e2e`, o Playwright baixa o Chromium. A CI
(`.github/workflows/ci.yml`) roda os dois comandos em jobs separados.

## Arquitetura

- `game`: regras puras (composição, portão da primeira peça, confirmações, revelação), sem
  framework, testadas sem HTTP.
- `room`: salas em memória com lock por sala e retrato imutável para leitura, controller REST
  (`/api`), expiração por varredura agendada e respostas de erro em `application/problem+json`.
  O contrato está descrito na própria API: visão sempre do ponto de vista de quem pede, `ETag` com
  `304` no polling.
- Frontend em HTML, CSS e JavaScript nativos (`src/main/resources/static`), sem build: módulos ES,
  polling encadeado de 2 s com recuo em falhas (2, 4, 8 e 15 s).
- Segurança: credencial em cookie `HttpOnly; SameSite=Strict` (o servidor guarda só o hash),
  convite de uso único separado da credencial, mutações exigem o cabeçalho `X-Duora`, e `Origin` e
  `Host` são conferidos contra uma allowlist local (proteção contra CSRF e DNS rebinding).

## Assets

A cena é renderizada no Blender a partir de `assets/source/blender/cena.py`; dois props vêm de
modelos gerados no Hi3D. Origem, licença e status de cada arquivo estão em
[`assets/manifest.md`](assets/manifest.md), assim como o comando para regenerar os renders. Os GLBs
originais são grandes e ficam fora do Git.

## Limitações

- Só roda localmente (escuta em `127.0.0.1`); não há configuração de implantação.
- O estado fica em memória: reiniciar o servidor apaga todas as salas.
- A arte (céu, terraço e props gerados) e o áudio são candidatos a final e ainda aguardam o aceite visual e o playtest.
- Não há contas nem histórico: a identidade é o cookie da sessão, e o terraço expira após 30
  minutos sem ação.
