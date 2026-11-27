package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.notificacao.infra.DespachanteDaOutbox;
import bipotheking.net.duora.notificacao.infra.EmailNoLog;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** O canal público de denúncia: sem conta, com Turnstile, limite por IP e aviso sem o conteúdo. */
@ExtendWith(OutputCaptureExtension.class)
class DenunciaPublicaApiTest extends BancoDeTeste {

  private static final String MENSAGEM =
      "Recebemos sua denúncia. Se você deixou um e-mail, respondemos por lá.";
  private static final String TOKEN_BOM = "token-do-widget";
  private static final String DESCRICAO = "Alguém pediu dinheiro na sala.";

  @Autowired private VerificacaoHumanaDeTeste verificacaoHumana;
  @Autowired private EmailNoLog emailNoLog;
  @Autowired private DespachanteDaOutbox despachante;

  @Value("${duora.contato.email}")
  private String contato;

  @Value("${duora.url-publica}")
  private String urlPublica;

  @BeforeEach
  void zerarVerificacaoHumana() {
    verificacaoHumana.zerar();
  }

  @Test
  void semSessaoAceita202() throws Exception {
    denunciar(corpo("GOLPE", DESCRICAO, null, null, TOKEN_BOM))
        .andExpect(status().isAccepted())
        .andExpect(content().json("{\"mensagem\":\"" + MENSAGEM + "\"}", JsonCompareMode.STRICT));

    assertThat(situacoes()).containsExactly("PENDENTE");
    assertThat(
            jdbc.sql("select criada_em from denuncia_publica")
                .query(OffsetDateTime.class)
                .single()
                .toInstant())
        .isEqualTo(INICIO);
  }

  @Test
  void turnstileRecusado422ENadaGrava() throws Exception {
    denunciar(corpo("GOLPE", DESCRICAO, null, null, VerificacaoHumanaDeTeste.TOKEN_RECUSADO))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("VERIFICACAO_HUMANA_FALHOU"));

    assertThat(situacoes()).isEmpty();
    assertThat(mensagens()).isZero();
  }

  @Test
  void sextaNaHoraRecebe429() throws Exception {
    RequestPostProcessor mesmoIp = OrigemDeTeste.ip("10.40.50.60");
    for (int denuncia = 1; denuncia <= 5; denuncia++) {
      denunciar(corpo("GOLPE", DESCRICAO, null, null, TOKEN_BOM), mesmoIp)
          .andExpect(status().isAccepted());
    }

    denunciar(corpo("GOLPE", DESCRICAO, null, null, TOKEN_BOM), mesmoIp)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    assertThat(situacoes()).hasSize(5);
  }

  @Test
  void descricaoCom9Recebe422() throws Exception {
    denunciar(corpo("GOLPE", "x".repeat(9), null, null, TOKEN_BOM))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DENUNCIA_PUBLICA_INVALIDA"))
        .andExpect(jsonPath("$.mensagem").value("Conte o que aconteceu em 10 a 2000 caracteres."));

    assertThat(situacoes()).isEmpty();
  }

  @Test
  void descricaoCom10Aceita() throws Exception {
    denunciar(corpo("GOLPE", "x".repeat(10), null, null, TOKEN_BOM))
        .andExpect(status().isAccepted());

    assertThat(situacoes()).containsExactly("PENDENTE");
  }

  @Test
  void descricaoCom2001Recebe422() throws Exception {
    denunciar(corpo("GOLPE", "x".repeat(2001), null, null, TOKEN_BOM))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DENUNCIA_PUBLICA_INVALIDA"))
        .andExpect(jsonPath("$.mensagem").value("Conte o que aconteceu em 10 a 2000 caracteres."));

    assertThat(situacoes()).isEmpty();
  }

  @Test
  void referenciaCom201Recebe422() throws Exception {
    denunciar(corpo("GOLPE", DESCRICAO, "r".repeat(201), null, TOKEN_BOM))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DENUNCIA_PUBLICA_INVALIDA"))
        .andExpect(jsonPath("$.mensagem").value("Use até 200 caracteres no apelido ou link."));

    assertThat(situacoes()).isEmpty();
  }

  @Test
  void motivoDesconhecidoRecebe422() throws Exception {
    denunciar(corpo("SPAM", DESCRICAO, null, null, TOKEN_BOM))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DENUNCIA_PUBLICA_INVALIDA"))
        .andExpect(jsonPath("$.mensagem").value("Escolha o que aconteceu."));

    assertThat(situacoes()).isEmpty();
  }

  @Test
  void emailDeContatoInvalidoRecebe422() throws Exception {
    denunciar(corpo("GOLPE", DESCRICAO, null, "sem-arroba", TOKEN_BOM))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DENUNCIA_PUBLICA_INVALIDA"))
        .andExpect(jsonPath("$.mensagem").value("Confira o e-mail para resposta."));

    assertThat(situacoes()).isEmpty();
  }

  @Test
  void emailDoAvisoNaoTrazADescricao() throws Exception {
    String descricao = "descricao-canario-" + UUID.randomUUID();
    String referencia = "referencia-canario-" + UUID.randomUUID();

    denunciar(corpo("ASSEDIO", descricao, referencia, null, TOKEN_BOM))
        .andExpect(status().isAccepted());

    assertThat(jdbc.sql("select carga::text from outbox").query(String.class).single())
        .doesNotContain(descricao, referencia);
    despachante.despacharAgora();
    MensagemDeEmail aviso = emailNoLog.ultimaPara(contato).orElseThrow();
    assertThat(aviso.assunto()).isEqualTo("Duora: denúncia pública recebida");
    assertThat(aviso.texto()).isEqualTo("Motivo: ASSEDIO\n\n" + urlPublica + "/admin");
    assertThat(aviso.html()).doesNotContain(descricao, referencia);
  }

  @Test
  void campoExtraRecebe400() throws Exception {
    denunciar(
            "{\"motivo\":\"GOLPE\",\"descricao\":\""
                + DESCRICAO
                + "\",\"tokenVerificacao\":\""
                + TOKEN_BOM
                + "\",\"situacao\":\"RESOLVIDA\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

    assertThat(situacoes()).isEmpty();
  }

  @Test
  void logNaoTrazDescricaoReferenciaNemEmailDeContato(CapturedOutput saida) throws Exception {
    String descricao = "descricao-canario-" + UUID.randomUUID();
    String referencia = "referencia-canario-" + UUID.randomUUID();
    String emailContato = "canario-" + UUID.randomUUID() + "@exemplo.com";

    denunciar(corpo("OUTRO", descricao, referencia, emailContato, TOKEN_BOM))
        .andExpect(status().isAccepted());
    despachante.despacharAgora();

    assertThat(emailNoLog.ultimaPara(contato)).isPresent();
    assertThat(saida.getAll()).doesNotContain(descricao, referencia, emailContato);
  }

  private ResultActions denunciar(String corpo) throws Exception {
    return denunciar(corpo, OrigemDeTeste.ipAleatorio());
  }

  private ResultActions denunciar(String corpo, RequestPostProcessor ip) throws Exception {
    return mvc.perform(
        post("/api/denuncias-publicas")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .with(ip));
  }

  /** Os valores do teste não têm aspas nem barras: dispensam escape no JSON. */
  private static String corpo(
      String motivo,
      String descricao,
      @Nullable String referencia,
      @Nullable String emailContato,
      String tokenVerificacao) {
    StringBuilder json =
        new StringBuilder()
            .append("{\"motivo\":\"")
            .append(motivo)
            .append("\",\"descricao\":\"")
            .append(descricao)
            .append('"');
    if (referencia != null) {
      json.append(",\"referencia\":\"").append(referencia).append('"');
    }
    if (emailContato != null) {
      json.append(",\"emailContato\":\"").append(emailContato).append('"');
    }
    return json.append(",\"tokenVerificacao\":\"")
        .append(tokenVerificacao)
        .append("\"}")
        .toString();
  }

  private List<String> situacoes() {
    return jdbc.sql("select situacao from denuncia_publica").query(String.class).list();
  }

  private long mensagens() {
    return jdbc.sql("select count(*) from outbox").query(Long.class).single();
  }
}
