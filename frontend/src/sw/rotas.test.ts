import { describe, expect, it } from "vitest";
import { rotaDoPedido, type Rota } from "./rotas";

const ORIGEM = "https://duora.app";

interface Pedido {
  caminho: string;
  metodo?: string;
  modo?: RequestMode;
  temRange?: boolean;
  origem?: string;
}

function rota({ caminho, metodo = "GET", modo = "cors", temRange = false, origem = ORIGEM }: Pedido): Rota {
  return rotaDoPedido({ url: new URL(caminho, ORIGEM), origem, metodo, modo, temRange });
}

describe("rotaDoPedido", () => {
  it("POST é ignorado", () => {
    expect(rota({ caminho: "/app/index-abc.js", metodo: "POST" })).toBe("ignorar");
  });

  it("POST de navegação é ignorado", () => {
    expect(rota({ caminho: "/", metodo: "POST", modo: "navigate" })).toBe("ignorar");
  });

  it("a API é ignorada", () => {
    expect(rota({ caminho: "/api/sala" })).toBe("ignorar");
  });

  it("pedido com Range é ignorado", () => {
    expect(rota({ caminho: "/assets/cenarios/terraco/audio/trilha.mp3", temRange: true })).toBe("ignorar");
  });

  it("outra origem é ignorada", () => {
    expect(rota({ caminho: "/app/index-abc.js", origem: "https://outro.exemplo" })).toBe("ignorar");
  });

  it("navegação vem da rede", () => {
    expect(rota({ caminho: "/", modo: "navigate" })).toBe("navegacao");
  });

  it("o JSON de /assets/ vem da rede e renova a cópia guardada", () => {
    expect(rota({ caminho: "/assets/cenarios/terraco/cena/cena.json" })).toBe("rede-guardando");
  });

  it("os binários de /assets/ vêm do cache primeiro", () => {
    expect(rota({ caminho: "/assets/cenarios/terraco/cena/banco-noite-900.webp" })).toBe("cache-primeiro");
  });

  // Bundle novo com o worker antigo no controle: o arquivo /app/ novo não está no cache e vem da rede.
  it("os arquivos com hash de /app/ vêm do cache primeiro", () => {
    expect(rota({ caminho: "/app/index-abc.js" })).toBe("cache-primeiro");
  });

  // O documento da verificação tem CSP e Permissions-Policy próprias: nunca o shell guardado com as de "/".
  it("a navegação para a verificação é ignorada", () => {
    expect(rota({ caminho: "/verificacao", modo: "navigate" })).toBe("ignorar");
  });

  it("a navegação para a verificação com query também é ignorada", () => {
    expect(rota({ caminho: "/verificacao?de=conta", modo: "navigate" })).toBe("ignorar");
  });

  // O chunk do Amplify (vários MB) só serve à verificação: não entra no cache do worker.
  it("o chunk do detector do Amplify é ignorado", () => {
    expect(rota({ caminho: "/app/verificacao-amplify-abc.js" })).toBe("ignorar");
  });

  it("o CSS do detector do Amplify é ignorado", () => {
    expect(rota({ caminho: "/app/verificacao-amplify-abc.css" })).toBe("ignorar");
  });

  it("o resto vem da rede", () => {
    expect(rota({ caminho: "/manifest.webmanifest" })).toBe("rede");
  });
});
