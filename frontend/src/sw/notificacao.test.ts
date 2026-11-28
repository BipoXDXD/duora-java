import { describe, expect, it } from "vitest";
import { corpoDaInscricao, janelaParaFocar, notificacaoDoPush, urlSegura } from "./notificacao";

const ORIGEM = "https://duora.app";

describe("notificacaoDoPush", () => {
  it("compativelTemTextoFixoETag", () => {
    expect(notificacaoDoPush('{"tipo":"compativel"}')).toEqual({
      titulo: "Duora",
      opcoes: {
        body: "Alguém compatível está esperando agora.",
        tag: "duora-compativel",
        renotify: false,
        icon: "/assets/icones/icone-192.png",
        data: { url: "/encontrar" },
      },
    });
  });

  // A tela bloqueada nunca mostra apelido, distância ou imagem vindos do payload.
  it("payloadComCorpoEImagemMostraSoOTextoFixo", () => {
    const { titulo, opcoes } = notificacaoDoPush(
      JSON.stringify({ tipo: "compativel", corpo: "Ana, 2 km", image: "x", url: "https://evil.example" }),
    );

    expect(titulo).toBe("Duora");
    expect(opcoes.body).toBe("Alguém compatível está esperando agora.");
    expect(opcoes).not.toHaveProperty("image");
    expect(opcoes.data).toEqual({ url: "/encontrar" });
    expect(JSON.stringify(opcoes)).not.toContain("Ana");
  });

  it.each([null, "", "não é JSON", "null", "[]", '"compativel"', '{"tipo":"outro"}', '{"tipo":1}'])(
    "payloadInvalidoViraGenerico (%s)",
    (texto) => {
      expect(notificacaoDoPush(texto)).toEqual({
        titulo: "Duora",
        opcoes: {
          body: "Abra o Duora para ver a novidade.",
          tag: "duora",
          renotify: false,
          icon: "/assets/icones/icone-192.png",
          data: { url: "/" },
        },
      });
    },
  );
});

describe("urlSegura", () => {
  it.each(["/encontrar", "/encontrar/fila?x=1", "/"])("caminho interno %s passa", (url) => {
    expect(urlSegura(url)).toBe(url);
  });

  // O URL parser descarta tab e quebra de linha: "/\t/evil" viraria "//evil".
  it.each([
    "https://evil.example",
    "//evil.example",
    "/\\evil",
    "javascript:x",
    "/\t/evil.example",
    "/\n/evil.example",
    "",
    "encontrar",
    undefined,
    null,
    42,
  ])("urlExternaVira / (%s)", (url) => {
    expect(urlSegura(url)).toBe("/");
  });
});

describe("janelaParaFocar", () => {
  it("janelaNoMesmoCaminhoEFocada", () => {
    const outra = { url: `${ORIGEM}/perfil` };
    const certa = { url: `${ORIGEM}/encontrar?aba=1` };

    expect(janelaParaFocar([outra, certa], "/encontrar", ORIGEM)).toBe(certa);
  });

  it("janelaDeOutroCaminhoNaoConta", () => {
    expect(janelaParaFocar([{ url: `${ORIGEM}/encontrar/fila` }], "/encontrar", ORIGEM)).toBeUndefined();
  });

  it("janela de outra origem não conta", () => {
    expect(janelaParaFocar([{ url: "https://outro.exemplo/encontrar" }], "/encontrar", ORIGEM)).toBeUndefined();
  });
});

describe("corpoDaInscricao", () => {
  it("leva o endpoint e as chaves no formato do contrato", () => {
    expect(
      corpoDaInscricao({
        endpoint: "https://push.exemplo/abc",
        keys: { p256dh: "p", auth: "a" },
        expirationTime: null,
      }),
    ).toEqual({ endpoint: "https://push.exemplo/abc", chaves: { p256dh: "p", auth: "a" } });
  });

  it.each<PushSubscriptionJSON>([
    { endpoint: "https://push.exemplo/abc" },
    { endpoint: "https://push.exemplo/abc", keys: { p256dh: "p" } },
    { endpoint: "https://push.exemplo/abc", keys: { auth: "a" } },
    { keys: { p256dh: "p", auth: "a" } },
  ])("inscricaoSemChavesViraNull (%o)", (json) => {
    expect(corpoDaInscricao(json)).toBeNull();
  });
});
