// @vitest-environment node
import { existsSync, readFileSync, realpathSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { describe, expect, it } from "vitest";

// A cadeia de suprimentos do detector de prova de vida, pinada: a versão exata no package.json, a
// integridade no lockfile, o que o pnpm instalou e as URLs de onde o detector baixa o wasm e o modelo.
// Falhar aqui é o objetivo: subir o pacote ou trocar a origem exige rever a CSP (F1) e este pino.

const RAIZ_DO_FRONT = join(import.meta.dirname, "..", "..", "..");
const LIVENESS = "@aws-amplify/ui-react-liveness";
const VERSAO_DO_LIVENESS = "3.7.0";
const VERSAO_DO_AMPLIFY = "6.22.2";
const VERSAO_DO_WASM = "4.11.0";
const INTEGRIDADE_DO_LIVENESS =
  "sha512-dIxvhFGowfhsW0YMVntzhx624W2kSvfw2mVt8ZAr14x/MM8qyD8iOw5vRH3dRmgnuVqUG7H9/Q6et+pV1xnd4w==";
const INTEGRIDADE_DO_WASM =
  "sha512-ubv+5cXux8al/ccZvmDpuw5PNm8FAj57GHzC0nDkX6lHJ0iXNklzUPK5SoTYnU7eYwXXzv8synjnqiqnWvfDEA==";
/** A mesma literal do connect-src em CspDaVerificacaoTest (F1). */
const ORIGEM_DO_DETECTOR = "https://cdn.liveness.rekognition.amazonaws.com";
const WASM = ["tfjs-backend-wasm.wasm", "tfjs-backend-wasm-simd.wasm", "tfjs-backend-wasm-threaded-simd.wasm"];

interface PacoteJson {
  version: string;
  dependencies?: Record<string, string>;
}

function lerJson(caminho: string): PacoteJson {
  return JSON.parse(readFileSync(caminho, "utf-8")) as PacoteJson;
}

/** A pasta real do pacote instalado (o pnpm liga node_modules/<pacote> ao store em .pnpm). */
function pastaDoLiveness(): string {
  return realpathSync(join(RAIZ_DO_FRONT, "node_modules", LIVENESS));
}

/** O tfjs-backend-wasm que o liveness resolve, não um outro que o app tenha por acaso. */
function pastaDoWasm(): string {
  const doLiveness = createRequire(join(pastaDoLiveness(), "package.json"));
  return dirname(doLiveness.resolve("@tensorflow/tfjs-backend-wasm/package.json"));
}

describe("cadeia de suprimentos do detector", () => {
  it("versaoExataDoPacote", () => {
    const dependencias = lerJson(join(RAIZ_DO_FRONT, "package.json")).dependencies;

    expect(dependencias?.[LIVENESS]).toBe(VERSAO_DO_LIVENESS);
    expect(dependencias?.["aws-amplify"]).toBe(VERSAO_DO_AMPLIFY);
  });

  it("lockfileTravaVersaoEIntegridade", () => {
    const lockfile = readFileSync(join(RAIZ_DO_FRONT, "pnpm-lock.yaml"), "utf-8");

    expect(lockfile).toContain(
      `  '${LIVENESS}@${VERSAO_DO_LIVENESS}':\n    resolution: {integrity: ${INTEGRIDADE_DO_LIVENESS}}`,
    );
    expect(lockfile).toContain(
      `  '@tensorflow/tfjs-backend-wasm@${VERSAO_DO_WASM}':\n    resolution: {integrity: ${INTEGRIDADE_DO_WASM}}`,
    );
    expect(lockfile.match(/'@tensorflow\/tfjs-backend-wasm@[^']+':\n {4}resolution/g)).toHaveLength(1);
  });

  it("instaladoNaVersaoFixadaComOsTresWasm", () => {
    const liveness = lerJson(join(pastaDoLiveness(), "package.json"));
    const wasm = lerJson(join(pastaDoWasm(), "package.json"));

    expect(liveness.version).toBe(VERSAO_DO_LIVENESS);
    expect(liveness.dependencies?.["@tensorflow/tfjs-backend-wasm"]).toBe(VERSAO_DO_WASM);
    expect(wasm.version).toBe(VERSAO_DO_WASM);
    expect(WASM.filter((arquivo) => !existsSync(join(pastaDoWasm(), "dist", arquivo)))).toEqual([]);
  });

  // Sem binaryPath nem faceModelUrl no DetectorAmplify, valem os padrões do pacote: o wasm na versão
  // instalada e o modelo, os dois na origem que a CSP libera.
  it("detectorCarregaDasUrlsFixadas", () => {
    const fonte = readFileSync(
      join(pastaDoLiveness(), "dist/esm/components/FaceLivenessDetector/service/utils/blazefaceFaceDetection.mjs"),
      "utf-8",
    );
    // O github.com só aparece no texto de um erro (link de documentação), nunca é baixado.
    const origens = new Set(
      [...fonte.matchAll(/https:\/\/[^/`'"\s]+/g)].map(([url]) => url).filter((url) => url !== "https://github.com"),
    );

    expect([...origens]).toEqual([ORIGEM_DO_DETECTOR]);
    expect(fonte).toContain(
      "const DEFAULT_TFJS_WASM_URL = `https://cdn.liveness.rekognition.amazonaws.com/face-detection/tensorflow/tfjs-backend-wasm/${version_wasm}/`;",
    );
    expect(fonte).toContain(
      "const DEFAULT_BLAZEFACE_URL = `https://cdn.liveness.rekognition.amazonaws.com/face-detection/tensorflow-models/blazeface/${BLAZEFACE_VERSION}/model/model.json`;",
    );
    expect(fonte).toContain("const BLAZEFACE_VERSION = '1.0.2';");
  });
});
