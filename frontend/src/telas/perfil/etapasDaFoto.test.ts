import { describe, expect, it } from "vitest";
import { ErroHttp } from "../../api/http";
import { conferirNoCliente, TAMANHO_MAXIMO, transicao, type EtapaDaFoto } from "./etapasDaFoto";

function arquivo(tamanho: number, tipo = "image/jpeg"): File {
  return new File([new Uint8Array(tamanho)], "foto", { type: tipo });
}

const ESCOLHA = { arquivo: arquivo(10), previa: "data:image/jpeg;base64,AA==", chave: "chave-original" };
const ENVIANDO: EtapaDaFoto = { tipo: "enviando", ...ESCOLHA };

describe("etapasDaFoto", () => {
  it("arquivoDe5MBMais1Recusado", () => {
    expect(conferirNoCliente(arquivo(TAMANHO_MAXIMO + 1))).toEqual({ tipo: "grande" });
  });

  it("arquivoDeExatos5MBAceito", () => {
    expect(conferirNoCliente(arquivo(5_242_880))).toBeNull();
  });

  it("gifRecusadoNoCliente", () => {
    expect(conferirNoCliente(arquivo(10, "image/gif"))).toEqual({ tipo: "formato" });
  });

  it("tipoVazioVaiAoServidor", () => {
    expect(conferirNoCliente(arquivo(10, ""))).toBeNull();
  });

  it("retentativaMantemAChave", () => {
    const falhou = transicao(ENVIANDO, { tipo: "envioRecusado", erro: new ErroHttp({ status: 503, codigo: "X" }) });

    const deNovo = transicao(falhou, { tipo: "enviar" });

    expect(falhou).toMatchObject({ tipo: "falhou", aviso: { tipo: "indisponivel" } });
    expect(deNovo).toMatchObject({ tipo: "enviando", chave: "chave-original" });
  });

  it("chaveReusadaGeraChaveNova", () => {
    const erro = new ErroHttp({ status: 409, codigo: "CHAVE_DE_IDEMPOTENCIA_REUSADA" });

    const falhou = transicao(ENVIANDO, { tipo: "envioRecusado", erro }, () => "chave-nova");

    expect(falhou).toMatchObject({ tipo: "falhou", chave: "chave-nova", aviso: { tipo: "indisponivel" } });
  });

  it("limiteSemRetryAfterEspera60", () => {
    const erro = new ErroHttp({ status: 429, codigo: "LIMITE_DE_TAXA" });

    expect(transicao(ENVIANDO, { tipo: "envioRecusado", erro })).toMatchObject({
      aviso: { tipo: "limite", segundos: 60 },
    });
  });

  it("escolherDuranteEnvioNaoTroca", () => {
    const outra = { arquivo: arquivo(20), previa: "data:image/png;base64,BB==", chave: "outra" };

    expect(transicao(ENVIANDO, { tipo: "escolheu", ...outra })).toBe(ENVIANDO);
  });
});
