package bipotheking.net.duora.room;

import org.jspecify.annotations.Nullable;

/** Corpos aceitos pela API. Campos desconhecidos são recusados pelo Jackson. */
final class Pedidos {

  private Pedidos() {}

  record CriarSala(@Nullable String apelido) {}

  record Entrar(@Nullable String convite, @Nullable String apelido) {
    @Override
    public String toString() {
      return "Entrar[convite=***]";
    }
  }

  record Colocar(@Nullable String peca, @Nullable String lugar, @Nullable Long versaoEsperada) {}

  record Sinalizar(@Nullable String peca) {}

  record Pronto(@Nullable Long versao, @Nullable Boolean pronto) {}
}
