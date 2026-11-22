import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { registrarServiceWorker } from "./pwa";

const register = vi.fn<ServiceWorkerContainer["register"]>();

beforeEach(() => {
  // O jsdom não tem service worker; o teste põe um container falso no navigator.
  Object.defineProperty(navigator, "serviceWorker", { value: { register }, configurable: true });
});

afterEach(() => {
  Reflect.deleteProperty(navigator, "serviceWorker");
  register.mockReset();
  vi.unstubAllEnvs();
});

describe("registrarServiceWorker", () => {
  it("no build de produção registra o /sw.js", () => {
    vi.stubEnv("PROD", true);

    registrarServiceWorker();

    expect(register).toHaveBeenCalledExactlyOnceWith("/sw.js", { scope: "/", type: "classic" });
  });

  it("no vite dev não registra: lá o /sw.js não existe", () => {
    vi.stubEnv("PROD", false);

    registrarServiceWorker();

    expect(register).not.toHaveBeenCalled();
  });
});
