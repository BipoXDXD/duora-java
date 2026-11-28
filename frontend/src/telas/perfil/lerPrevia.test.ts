import { describe, expect, it } from "vitest";
import { lerPrevia } from "./lerPrevia";

describe("lerPrevia", () => {
  it("previaEDataUrl", async () => {
    const foto = new Blob([new Uint8Array([137, 80, 78, 71])], { type: "image/png" });

    await expect(lerPrevia(foto)).resolves.toBe("data:image/png;base64,iVBORw==");
  });
});
