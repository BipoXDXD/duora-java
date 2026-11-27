package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DominiosDescartaveisTest {

  private final DominiosDescartaveis descartaveis =
      new DominiosDescartaveis(
          List.of("# lista de teste", "", "mailinator.com", "  yopmail.com  "));

  @ParameterizedTest
  @ValueSource(strings = {"mailinator.com", "sub.mailinator.com", "yopmail.com"})
  void dominioDaListaOuSubdominioEhDescartavel(String dominio) {
    assertThat(descartaveis.contem(dominio)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"gmail.com", "notmailinator.com", "com", "# lista de teste", ""})
  void dominioForaDaListaNaoEhDescartavel(String dominio) {
    assertThat(descartaveis.contem(dominio)).isFalse();
  }
}
