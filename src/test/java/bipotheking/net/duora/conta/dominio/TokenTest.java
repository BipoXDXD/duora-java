package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.nucleo.Resumos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class TokenTest {

  @Test
  void novoPede32BytesAoGerador() {
    GeradorDeBytesFixo gerador = new GeradorDeBytesFixo((byte) 0xFB);

    Token.novo(gerador);

    assertThat(gerador.pedidos).containsExactly(32);
  }

  @Test
  void novoCodificaEmBase64UrlSemPadding() {
    Token token = Token.novo(new GeradorDeBytesFixo((byte) 0xFB));

    // Três bytes 0xFB viram "-_v7" em base64url; os dois últimos viram "-_s", sem "=" no fim.
    assertThat(token.valor()).isEqualTo("-_v7".repeat(10) + "-_s").hasSize(43);
  }

  @Test
  void resumoEhOSha256DoValor() {
    Token token = Token.de("qualquer-valor");

    assertThat(token.resumo()).isEqualTo(Resumos.sha256("qualquer-valor"));
  }

  @Test
  void toStringNaoExpoeOValor() {
    assertThat(Token.de("segredo")).hasToString("Token[***]");
  }

  /** Enche todo pedido de bytes com o mesmo valor e anota o tamanho de cada pedido. */
  private static final class GeradorDeBytesFixo implements RandomGenerator {

    private final byte valor;
    private final List<Integer> pedidos = new ArrayList<>();

    GeradorDeBytesFixo(byte valor) {
      this.valor = valor;
    }

    @Override
    public void nextBytes(byte[] bytes) {
      pedidos.add(bytes.length);
      Arrays.fill(bytes, valor);
    }

    @Override
    public long nextLong() {
      throw new UnsupportedOperationException("O token só pode usar nextBytes");
    }
  }
}
