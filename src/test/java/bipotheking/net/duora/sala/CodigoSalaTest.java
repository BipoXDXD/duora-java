package bipotheking.net.duora.sala;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CodigoSalaTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          PERFIL_INCOMPLETO    | REGRA_DE_NEGOCIO | Complete seu perfil em Meu perfil para entrar na fila.
          CONTA_PAUSADA        | REGRA_DE_NEGOCIO | Sua conta está pausada. Volte a aparecer em Meu perfil para entrar na fila.
          JA_EM_ENCONTRO       | CONFLITO         | Você já está num encontro. Termine esse antes de começar outro.
          FORA_DA_FILA         | NAO_ENCONTRADO   | Você não está na fila.
          ENCONTRO_INEXISTENTE | NAO_ENCONTRADO   | Este encontro não existe mais.
          CONVITE_INVALIDO     | NAO_ENCONTRADO   | Este convite não vale mais.
          CONVITE_PROPRIO      | REGRA_DE_NEGOCIO | Este convite é seu. Mande o link para outra pessoa.
          CAPACIDADE_CHEIA     | INDISPONIVEL     | Todos os encontros estão ocupados. Tente de novo em 1 minuto.
          CENARIO_INVALIDO     | REGRA_DE_NEGOCIO | Escolha um dos cenários da lista.
          """)
  void codigoTemTipoEMensagemDoContrato(CodigoSala codigo, TipoDeErro tipo, String mensagem) {
    assertThat(codigo.tipo()).isEqualTo(tipo);
    assertThat(codigo.mensagem()).isEqualTo(mensagem);
  }

  @Test
  void contratoTemNoveCodigos() {
    assertThat(CodigoSala.values()).hasSize(9);
  }

  @Test
  void erroLevaOCodigoEAMensagem() {
    ErroDeNegocio erro = CodigoSala.FORA_DA_FILA.erro();

    assertThat(erro.codigo()).isEqualTo(CodigoSala.FORA_DA_FILA);
    assertThat(erro.getMessage()).isEqualTo("Você não está na fila.");
  }
}
