import type { FaceLivenessDetectorCoreProps } from "@aws-amplify/ui-react-liveness";
import { describe, expect, expectTypeOf, it } from "vitest";
import { TEXTOS_DO_DETECTOR, type TextosDoDetector } from "./textosDoDetector";

/** O pacote não exporta o LivenessDisplayText; a prop displayText do Core é ele. */
type LivenessDisplayText = NonNullable<FaceLivenessDetectorCoreProps["displayText"]>;

describe("TEXTOS_DO_DETECTOR", () => {
  // Uma chave nova no pacote apareceria em inglês na tela: o tipo obriga a traduzi-la.
  it("cobreTodasAsChavesDoDisplayText", () => {
    expectTypeOf(TEXTOS_DO_DETECTOR).toExtend<TextosDoDetector>();
    expectTypeOf(TEXTOS_DO_DETECTOR).toExtend<LivenessDisplayText>();
    // Só as quatro chaves obsoletas do aviso de luz ficam de fora.
    expectTypeOf<
      Exclude<keyof Required<LivenessDisplayText>, keyof typeof TEXTOS_DO_DETECTOR>
    >().toEqualTypeOf<`photosensitivyWarning${"Body" | "Heading" | "Info" | "Label"}Text`>();
    expect(Object.values(TEXTOS_DO_DETECTOR).every((texto) => texto.trim() !== "")).toBe(true);
  });

  it("semFlexaoDeGenero", () => {
    const flexionados = Object.values(TEXTOS_DO_DETECTOR).filter((texto) =>
      /\b(sozinh[oa]|pront[oa]|obrigad[oa])\b/i.test(texto),
    );

    expect(flexionados).toEqual([]);
  });

  it("textosProprios", () => {
    expect(TEXTOS_DO_DETECTOR.hintTooManyFacesText).toBe("Só uma pessoa por vez");
    expect(TEXTOS_DO_DETECTOR.multipleFacesMessageText).toBe("Fique sem outra pessoa no quadro.");
    expect(TEXTOS_DO_DETECTOR.hintVerifyingText).toBe("Conferindo a selfie…");
    expect(TEXTOS_DO_DETECTOR.a11yVideoLabelText).toBe("Prévia da câmera para a selfie");
  });
});
