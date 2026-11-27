package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import bipotheking.net.duora.nucleo.TipoDeErro;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CodigoVerificacaoTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          CONSENTIMENTO_BIOMETRICO_NECESSARIO   | REGRA_DE_NEGOCIO | Concorde com o uso da sua imagem para confirmar sua idade.
          VERIFICACAO_FORA_DE_ORDEM             | CONFLITO         | Esta etapa da verificação não está disponível agora. Recarregue a página.
          SESSAO_DE_VERIFICACAO_INVALIDA        | NAO_ENCONTRADO   | Esta verificação expirou. Comece de novo.
          REFAZER_PROVA_DE_VIDA                 | CONFLITO         | A selfie venceu antes do envio. Faça uma nova.
          DOCUMENTO_INVALIDO                    | REGRA_DE_NEGOCIO | Envie fotos JPEG ou PNG de até 5 MB, frente e verso.
          LIMITE_DE_VERIFICACAO                 | LIMITE           | Você chegou ao limite de tentativas de hoje. Tente de novo amanhã.
          PROVA_DE_VIDA_INDISPONIVEL            | INDISPONIVEL     | A verificação está fora do ar. Tente de novo em alguns minutos.
          CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL | INDISPONIVEL     | A conferência do documento está fora do ar. Tente de novo em alguns minutos.
          """)
  void codigoTemTipoEMensagemDoContrato(
      CodigoVerificacao codigo, TipoDeErro tipo, String mensagem) {
    assertThat(codigo.tipo()).isEqualTo(tipo);
    assertThat(codigo.mensagem()).isEqualTo(mensagem);
  }

  @Test
  void contratoTemOitoCodigos() {
    assertThat(CodigoVerificacao.values()).hasSize(8);
  }

  @Test
  void limiteViraRetryAfter() {
    var erro = new LimiteDeVerificacaoExcedido(Duration.ofHours(2));

    assertThat(erro.tentarDeNovoEm()).contains(Duration.ofHours(2));
  }

  @Test
  void limiteUsaOCodigoDaVerificacao() {
    var erro = new LimiteDeVerificacaoExcedido(Duration.ofHours(2));

    assertThat(erro.codigo()).isEqualTo(CodigoVerificacao.LIMITE_DE_VERIFICACAO);
  }

  @Test
  void zeroEnviosRecusado() {
    assertThatThrownBy(() -> new LimitesDaVerificacao(3, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duora.verificacao.envios-de-documento-por-dia");
  }

  @Test
  void limitePorFinalidade() {
    var limites = new LimitesDaVerificacao(3, 2);

    assertThat(limites.limite(FinalidadeDaTentativa.SELFIE)).isEqualTo(3);
    assertThat(limites.limite(FinalidadeDaTentativa.DOCUMENTO)).isEqualTo(2);
  }
}
