package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/** Conteúdo obrigatório dos textos publicados, sobre os arquivos reais de {@code legal/}. */
class ConteudoLegalTest {

  private static final String CONTATO = "support@meetduora.com";
  private static final String EMAIL_DO_ENCARREGADO = "encarregado@exemplo.invalid";
  private static final String NOME_DO_ENCARREGADO = "Pessoa Encarregada de Teste";

  private static final DocumentosLegais DOCUMENTOS =
      new DocumentosLegais(
          new DefaultResourceLoader(),
          "classpath:legal/",
          CONTATO,
          new EncarregadoDeDados(NOME_DO_ENCARREGADO, EMAIL_DO_ENCARREGADO));

  private static String texto(DocumentoLegal documento) {
    return DOCUMENTOS.html(documento).replaceAll("<[^>]+>", " ");
  }

  private static int palavras(DocumentoLegal documento) {
    return texto(documento).trim().split("\\s+").length;
  }

  @Test
  void termosTrazemOObrigatorio() {
    assertThat(texto(DocumentoLegal.TERMOS))
        .contains("18 anos", "verificação de idade", "nudez", "violência explícita", CONTATO);
  }

  @Test
  void termosComAte800Palavras() {
    assertThat(palavras(DocumentoLegal.TERMOS)).isLessThanOrEqualTo(800);
  }

  @Test
  void privacidadeTrazOObrigatorio() {
    assertThat(texto(DocumentoLegal.PRIVACIDADE))
        .contains(
            "cerca de 5 km",
            "30 dias após o fim do encontro",
            "Resend",
            "Cloudflare Turnstile",
            "Excluir conta",
            "selfie",
            "Amazon Web Services",
            "Estados Unidos",
            "RG ou da CNH",
            "frente e do verso do RG ou da CNH e uma selfie nova, que vão juntas para a Amazon"
                + " Web Services",
            "sem guardar as imagens",
            "Registramos a versão do consentimento que você aceitou e quando.",
            "6 meses",
            "encarregado",
            NOME_DO_ENCARREGADO,
            EMAIL_DO_ENCARREGADO);
  }

  /** A conferência do documento saiu da Didit (ADR 0007): a política não pode citá-la. */
  @Test
  void privacidadeNaoCitaADidit() {
    assertThat(texto(DocumentoLegal.PRIVACIDADE)).doesNotContainIgnoringCase("didit");
  }

  @Test
  void controladorLiteralENomeDoEncarregadoSoNoPapelDeEncarregado() {
    String privacidade = texto(DocumentoLegal.PRIVACIDADE);

    assertThat(privacidade)
        .contains("O controlador dos dados é o responsável pelo Duora.")
        .containsOnlyOnce(NOME_DO_ENCARREGADO)
        .contains("O encarregado pelo tratamento de dados pessoais é " + NOME_DO_ENCARREGADO + ".");
  }

  @Test
  void contatosViramLinksDeEmail() {
    assertThat(DOCUMENTOS.html(DocumentoLegal.TERMOS)).contains(mailto(CONTATO));
    assertThat(DOCUMENTOS.html(DocumentoLegal.PRIVACIDADE))
        .contains(mailto(CONTATO), mailto(EMAIL_DO_ENCARREGADO));
  }

  private static String mailto(String email) {
    return "href=\"mailto:" + email + "\"";
  }

  @Test
  void privacidadeComAte1300Palavras() {
    assertThat(palavras(DocumentoLegal.PRIVACIDADE)).isLessThanOrEqualTo(1300);
  }

  @Test
  void nenhumFalaEmSenhaNemSobraMarcador() {
    for (DocumentoLegal documento : DocumentoLegal.values()) {
      assertThat(texto(documento)).as(documento.caminho()).doesNotContain("senha", "{{");
    }
  }
}
