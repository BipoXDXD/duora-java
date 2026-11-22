// @vitest-environment node
import { createHash } from "node:crypto";
import { expect, it } from "vitest";
import { impressao } from "./impressao.ts";

const texto = (valor: string) => new TextEncoder().encode(valor);

it("a ordem dos arquivos não importa", () => {
  const a = { caminho: "a.txt", conteudo: texto("x") };
  const b = { caminho: "b/c.txt", conteudo: texto("yz") };
  const c = { caminho: "app/index-abc.js", conteudo: texto("console.log(1)") };

  expect(impressao([c, a, b])).toBe(impressao([a, b, c]));
});

// O mesmo algoritmo do ServiceWorkerVersaoTest, escrito por extenso: caminho, byte zero, conteúdo.
it("dá o valor conhecido", () => {
  const sha = createHash("sha256");
  sha.update("a.txt", "utf8");
  sha.update(Buffer.from([0]));
  sha.update("x", "utf8");
  sha.update("b/c.txt", "utf8");
  sha.update(Buffer.from([0]));
  sha.update("yz", "utf8");
  const esperado = sha.digest("hex").slice(0, 12);

  expect(
    impressao([
      { caminho: "b/c.txt", conteudo: texto("yz") },
      { caminho: "a.txt", conteudo: texto("x") },
    ]),
  ).toBe(esperado);
});
