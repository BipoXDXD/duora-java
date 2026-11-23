import { describe, expect, it, vi } from "vitest";
import { lerConvite, resumirConvite } from "./convite";

function enderecoCom(hash: string) {
  const local = { hash, pathname: "/", search: "?x=1" } as Location;
  const replaceState = vi.fn<History["replaceState"]>();
  const historia = { replaceState } as unknown as History;
  return { local, historia, replaceState };
}

describe("lerConvite", () => {
  it("lerConviteLimpaOHash", () => {
    const { local, historia, replaceState } = enderecoCom("#convite=abc");

    expect(lerConvite(local, historia)).toBe("abc");
    expect(replaceState).toHaveBeenCalledWith(null, "", "/?x=1");
  });

  it("sem convite devolve null e não mexe no endereço", () => {
    const { local, historia, replaceState } = enderecoCom("");

    expect(lerConvite(local, historia)).toBeNull();
    expect(replaceState).not.toHaveBeenCalled();
  });

  it("convite vazio devolve null, mas limpa o fragmento", () => {
    const { local, historia, replaceState } = enderecoCom("#convite=");

    expect(lerConvite(local, historia)).toBeNull();
    expect(replaceState).toHaveBeenCalledOnce();
  });
});

describe("resumirConvite", () => {
  it("devolve o SHA-256 em hex, como o conviteDaSala do servidor", async () => {
    // Vetor do FIPS 180-2.
    expect(await resumirConvite("abc")).toBe("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  });

  it("digest que rejeita devolve null", async () => {
    vi.stubGlobal("crypto", { subtle: { digest: () => Promise.reject(new Error("NotSupportedError")) } });
    try {
      expect(await resumirConvite("abc")).toBeNull();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("sem Web Crypto (contexto inseguro) devolve null", async () => {
    vi.stubGlobal("crypto", {});
    try {
      expect(await resumirConvite("abc")).toBeNull();
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
