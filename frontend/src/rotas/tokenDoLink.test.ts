import { describe, expect, it, vi } from "vitest";
import { lerTokenDoLink } from "./tokenDoLink";

function enderecoCom(pathname: string, hash: string) {
  const local = { hash, pathname, search: "" } as Location;
  const replaceState = vi.fn<History["replaceState"]>();
  const historia = { replaceState } as unknown as History;
  return { local, historia, replaceState };
}

describe("lerTokenDoLink", () => {
  it("leOTokenELimpaAUrl", () => {
    const { local, historia, replaceState } = enderecoCom("/entrar/confirmar", "#t=abc");

    expect(lerTokenDoLink(local, historia)).toBe("abc");
    expect(replaceState).toHaveBeenCalledWith(null, "", "/entrar/confirmar");
  });

  it("semFragmentoDevolveNull", () => {
    const { local, historia, replaceState } = enderecoCom("/entrar/confirmar", "");

    expect(lerTokenDoLink(local, historia)).toBeNull();
    expect(replaceState).not.toHaveBeenCalled();
  });

  it("tokenVazioDevolveNullMasLimpaAUrl", () => {
    const { local, historia, replaceState } = enderecoCom("/entrar/confirmar", "#t=");

    expect(lerTokenDoLink(local, historia)).toBeNull();
    expect(replaceState).toHaveBeenCalledOnce();
  });

  it("foraDaConfirmacaoNaoMexeNoFragmento", () => {
    const { local, historia, replaceState } = enderecoCom("/termos", "#t=abc");

    expect(lerTokenDoLink(local, historia)).toBeNull();
    expect(replaceState).not.toHaveBeenCalled();
  });
});
