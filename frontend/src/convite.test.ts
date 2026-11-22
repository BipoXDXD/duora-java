import { describe, expect, it, vi } from "vitest";
import { lerConvite } from "./convite";

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
