package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class DocumentosLegaisTest {

  private static final String CONTATO = "teste@exemplo.com";
  private static final EncarregadoDeDados ENCARREGADO =
      new EncarregadoDeDados("Pessoa encarregada", "dpo@exemplo.invalid");

  /** Calculado uma vez com {@code shasum -a 256} sobre o arquivo com os marcadores trocados. */
  private static final String RESUMO_DOS_TERMOS_VALIDOS =
      "f68937acb5e70d23070a2d939655d7cd2bc8b842ec6378b284cbe8e85e875d1a";

  private static DocumentosLegais de(String cenario) {
    return new DocumentosLegais(
        new DefaultResourceLoader(),
        "classpath:legal-teste/" + cenario + "/",
        CONTATO,
        ENCARREGADO);
  }

  @Test
  void leVersaoEResumo() {
    VersaoDeDocumento versao = de("valido").atual(DocumentoLegal.TERMOS);

    assertThat(versao).isEqualTo(new VersaoDeDocumento("2026-01-02", RESUMO_DOS_TERMOS_VALIDOS));
  }

  @Test
  void trocaMarcadoresDeContatoEEncarregado() {
    String html = de("valido").html(DocumentoLegal.TERMOS);

    assertThat(html)
        .contains(CONTATO, "Pessoa encarregada", "dpo@exemplo.invalid")
        .doesNotContain("{{");
  }

  @Test
  void autolinkDeEmailViraMailtoMesmoComHtmlEscapado() {
    String html = de("valido").html(DocumentoLegal.TERMOS);

    assertThat(html).contains("href=\"mailto:teste@exemplo.com\">teste@exemplo.com</a>");
  }

  @Test
  void htmlSemOFrontMatter() {
    String html = de("valido").html(DocumentoLegal.TERMOS);

    assertThat(html).startsWith("<h1>Termos de uso</h1>").doesNotContain("versao");
  }

  @Test
  void htmlBrutoEscapado() {
    String html = de("com-html").html(DocumentoLegal.TERMOS);

    assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>");
  }

  @Test
  void linkJavascriptSaneado() {
    String html = de("com-html").html(DocumentoLegal.TERMOS);

    assertThat(html).doesNotContain("href=\"javascript:");
  }

  @Test
  void semFrontMatterFalha() {
    assertThatThrownBy(() -> de("sem-front-matter"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sem-front-matter/termos.md");
  }

  @Test
  void versaoMalFormadaFalha() {
    assertThatThrownBy(() -> de("versao-invalida"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("versao-invalida/termos.md");
  }

  @Test
  void marcadorDesconhecidoFalha() {
    assertThatThrownBy(() -> de("marcador-desconhecido"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("marcador-desconhecido/termos.md")
        .hasMessageContaining("{{encarregado.nome}}");
  }

  @Test
  void arquivoAusenteFalha() {
    assertThatThrownBy(() -> de("sem-privacidade"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sem-privacidade/privacidade.md");
  }
}
