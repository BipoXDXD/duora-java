import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Sem globals do Vitest, a Testing Library não registra a limpeza sozinha.
afterEach(() => {
  cleanup();
});

/**
 * O jsdom 27 só reflete o atributo `open` do <dialog>: faltam showModal(), close() e o pedido de
 * fechar do Esc. O mínimo do navegador: showModal() e close() mexem no `open` (close() avisa com o
 * evento `close`, numa task seguinte), e o Esc dispara `cancel` (cancelável) no modal aberto, que fecha se ninguém
 * impedir. Topo, inércia do resto e foco inicial ficam com quem testa no navegador (e2e).
 */
function completarODialogo(): void {
  // Os testes do build rodam no ambiente node, sem DOM.
  if (typeof HTMLDialogElement === "undefined") {
    return;
  }
  const prototipo = HTMLDialogElement.prototype as Partial<HTMLDialogElement>;
  if (typeof prototipo.showModal === "function") {
    return;
  }
  const modais = new Set<HTMLDialogElement>();

  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    if (this.open) {
      throw new DOMException("O diálogo já está aberto.", "InvalidStateError");
    }
    this.open = true;
    modais.add(this);
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (!this.open) {
      return;
    }
    this.open = false;
    modais.delete(this);
    // Como no navegador, o `close` chega numa task depois: os effects de quem fechou já rodaram.
    setTimeout(() => {
      this.dispatchEvent(new Event("close"));
    }, 0);
  };

  document.addEventListener("keydown", (evento) => {
    if (evento.key !== "Escape" || evento.defaultPrevented) {
      return;
    }
    const ultimo = [...modais].filter((dialogo) => dialogo.isConnected && dialogo.open).at(-1);
    if (ultimo === undefined) {
      return;
    }
    const seguir = ultimo.dispatchEvent(new Event("cancel", { cancelable: true }));
    if (seguir) {
      ultimo.close();
    }
  });
}

completarODialogo();
