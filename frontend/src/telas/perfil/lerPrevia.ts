/**
 * A prévia da foto escolhida como data URL. Não é `blob:`: a CSP só libera `data:` em img-src, e a
 * data URL não deixa um objeto para revogar.
 */
export function lerPrevia(arquivo: Blob): Promise<string> {
  return new Promise((resolver, rejeitar) => {
    const leitor = new FileReader();
    leitor.onload = () => {
      if (typeof leitor.result === "string") {
        resolver(leitor.result);
      } else {
        rejeitar(new TypeError("a leitura não deu texto"));
      }
    };
    leitor.onerror = () => {
      rejeitar(leitor.error ?? new Error("a leitura falhou"));
    };
    leitor.readAsDataURL(arquivo);
  });
}
