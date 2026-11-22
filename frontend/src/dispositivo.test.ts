import { afterEach, describe, expect, it, vi } from "vitest";
import { ehToque } from "./dispositivo";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ehToque", () => {
  it("falso sem matchMedia", () => {
    vi.stubGlobal("matchMedia", undefined);

    expect(ehToque()).toBe(false);
  });

  it("verdadeiro na tela de toque sem hover", () => {
    const matchMedia = vi.fn(() => ({ matches: true }));
    vi.stubGlobal("matchMedia", matchMedia);

    expect(ehToque()).toBe(true);
    expect(matchMedia).toHaveBeenCalledWith("(hover: none) and (pointer: coarse)");
  });
});
