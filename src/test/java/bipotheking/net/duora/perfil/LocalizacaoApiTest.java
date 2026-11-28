package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;

/** A localização entra como coordenada, fica só como célula geohash e nunca volta para fora. */
@ExtendWith(OutputCaptureExtension.class)
class LocalizacaoApiTest extends BancoDeTeste {

  private static final String SAO_PAULO = "{\"latitude\":-23.5505,\"longitude\":-46.6333}";
  private static final String RIO = "{\"latitude\":-22.9068,\"longitude\":-43.1729}";

  /** Dígitos que não aparecem em lugar nenhum do sistema a não ser no corpo enviado. */
  private static final String CANARIO = "987654321";

  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private Localizacoes localizacoes;

  @Test
  void gravaSoACelula() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    gravar(conta, SAO_PAULO).andExpect(status().isNoContent());

    assertThat(colunas())
        .containsExactlyInAnyOrder("conta_id", "celula", "consentida_em", "atualizada_em");
    assertThat(linhaDe(conta)).isEqualTo(new Linha("6gyf4", INICIO, INICIO));
  }

  @Test
  void coordenadaNaoApareceNoLog(CapturedOutput saida) throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    gravar(conta, "{\"latitude\":-23." + CANARIO + ",\"longitude\":-46.6333}")
        .andExpect(status().isNoContent());

    assertThat(saida.getAll()).doesNotContain(CANARIO);
  }

  @Test
  void corpoMalformadoNaoLogaNemEcoaCoordenada(CapturedOutput saida) throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    String resposta =
        gravar(conta, "{\"latitude\":\"-23." + CANARIO + "x\",\"longitude\":-46.6333}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(resposta).doesNotContain(CANARIO);
    assertThat(saida.getAll()).doesNotContain(CANARIO);
    assertThat(linhas(conta)).isZero();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          {"latitude":91,"longitude":-46.6333}          | 422 | COORDENADA_INVALIDA | 91
          {"latitude":-23.5505,"longitude":-181}        | 422 | COORDENADA_INVALIDA | 181
          {"latitude":"x","longitude":-46.6333}         | 400 | CORPO_INVALIDO      | 46.6333
          {"latitude":NaN,"longitude":-46.6333}         | 400 | CORPO_INVALIDO      | 46.6333
          {"latitude":-23.5505}                         | 400 | CORPO_INVALIDO      | 23.5505
          """)
  void coordenadaInvalidaRecusada(String corpo, int status, String codigo, String numero)
      throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    String resposta =
        gravar(conta, corpo)
            .andExpect(status().is(status))
            .andExpect(jsonPath("$.codigo").value(codigo))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(resposta).doesNotContain(numero);
    assertThat(linhas(conta)).isZero();
  }

  @Test
  void coordenadaInvalidaTemAMensagemDoContrato() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    gravar(conta, "{\"latitude\":91,\"longitude\":0}")
        .andExpect(
            jsonPath("$.mensagem").value("Não conseguimos usar essa localização. Tente de novo."));
  }

  @Test
  void getNaoDevolveCelula() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    gravar(conta, SAO_PAULO).andExpect(status().isNoContent());

    mvc.perform(get("/api/perfil/localizacao").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"consentida\":true,\"atualizadaEm\":\"" + INICIO + "\"}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void semLocalizacaoGetDevolveDesativada() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    mvc.perform(get("/api/perfil/localizacao").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(
            content().json("{\"consentida\":false,\"atualizadaEm\":null}", JsonCompareMode.STRICT));
  }

  @Test
  void segundoPutMantemConsentimento() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    gravar(conta, SAO_PAULO).andExpect(status().isNoContent());
    relogio.avancar(Duration.ofHours(1));

    gravar(conta, RIO).andExpect(status().isNoContent());

    assertThat(linhaDe(conta))
        .isEqualTo(new Linha("75cm9", INICIO, INICIO.plus(Duration.ofHours(1))));
  }

  @Test
  void atualizarSemConsentimentoRecusado() {
    ContaDeTeste conta = contas.criarAtiva();

    assertThatThrownBy(() -> localizacoes.atualizar(conta.id(), -23.5505, -46.6333))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro ->
                assertThat(erro.codigo())
                    .isEqualTo(CodigoLocalizacao.LOCALIZACAO_SEM_CONSENTIMENTO));
    assertThat(linhas(conta)).isZero();
  }

  @Test
  void revogarApaga() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    gravar(conta, SAO_PAULO).andExpect(status().isNoContent());

    revogar(conta).andExpect(status().isNoContent());
    assertThat(linhas(conta)).isZero();

    revogar(conta).andExpect(status().isNoContent());
  }

  @Test
  void exclusaoDaContaApagaLocalizacao() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    gravar(conta, SAO_PAULO).andExpect(status().isNoContent());

    exclusao.excluir(conta.id(), true);

    assertThat(linhas(conta)).isZero();
  }

  private ResultActions gravar(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        put("/api/perfil/localizacao")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private ResultActions revogar(ContaDeTeste conta) throws Exception {
    return mvc.perform(
        delete("/api/perfil/localizacao")
            .header(MesmaOrigem.CABECALHO, "1")
            .cookie(cookieDe(conta)));
  }

  private List<String> colunas() {
    return jdbc.sql(
            """
            select column_name from information_schema.columns
            where table_schema = 'public' and table_name = 'localizacao'
            """)
        .query(String.class)
        .list();
  }

  private record Linha(String celula, Instant consentidaEm, Instant atualizadaEm) {}

  private Linha linhaDe(ContaDeTeste conta) {
    return jdbc.sql(
            "select celula, consentida_em, atualizada_em from localizacao where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query((rs, linha) ->
            new Linha(
                rs.getString("celula"),
                rs.getObject("consentida_em", OffsetDateTime.class).toInstant(),
                rs.getObject("atualizada_em", OffsetDateTime.class).toInstant()))
        .single();
  }

  private long linhas(ContaDeTeste conta) {
    return jdbc.sql("select count(*) from localizacao where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query(Long.class)
        .single();
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
