package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.notificacao.Outbox;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class ProcessadorDeEmailTest {

  private static final MensagemDeEmail MENSAGEM =
      new MensagemDeEmail("ana@exemplo.com", "Seu link de entrada", "Texto", "<p>Texto</p>");

  private record Envio(MensagemDeEmail mensagem, UUID chave) {}

  private final List<Envio> envios = new ArrayList<>();
  private final ProcessadorDeEmail processador =
      new ProcessadorDeEmail((mensagem, chave) -> envios.add(new Envio(mensagem, chave)));

  @Test
  void tipoEhEmail() {
    assertThat(processador.tipo()).isEqualTo("email").isEqualTo(Outbox.EMAIL);
  }

  @Test
  void chamaOEnvioComOIdDaMensagemComoChave() {
    UUID idDaMensagem = UUID.randomUUID();

    processador.processar(MENSAGEM, idDaMensagem);

    assertThat(envios).containsExactly(new Envio(MENSAGEM, idDaMensagem));
  }

  @Test
  void cargaGravadaPelaOutboxVoltaComoMensagemDeEmail() {
    JsonMapper json =
        JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    String gravada = json.writeValueAsString(MENSAGEM);

    assertThat(json.readValue(gravada, processador.tipoDaCarga())).isEqualTo(MENSAGEM);
  }
}
