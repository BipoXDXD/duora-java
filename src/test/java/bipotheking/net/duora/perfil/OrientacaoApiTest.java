package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;

/** Orientação: dado sensível, com consentimento próprio, só da própria conta. */
class OrientacaoApiTest extends BancoDeTeste {

  private static final String BISSEXUAL =
      "{\"orientacao\":\"BISSEXUAL\",\"mostrarOrientacao\":false,\"consentimento\":true}";

  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;

  @Test
  void getVazio() throws Exception {
    ContaDeTeste conta = comPerfil();

    mvc.perform(get("/api/perfil/orientacao").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"orientacao\":null,\"descricaoOrientacao\":null,"
                        + "\"mostrarOrientacao\":false,\"consentidaEm\":null}",
                    JsonCompareMode.STRICT));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"orientacao\":\"GAY\",\"mostrarOrientacao\":true,\"consentimento\":false}",
        "{\"orientacao\":\"GAY\",\"mostrarOrientacao\":true}"
      })
  void semConsentimento422(String corpo) throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(conta, corpo)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONSENTIMENTO_NECESSARIO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void comConsentimentoGravaOInstante() throws Exception {
    ContaDeTeste conta = comPerfil();
    relogio.avancar(Duration.ofMinutes(7));
    Instant agora = INICIO.plus(Duration.ofMinutes(7));

    salvar(conta, BISSEXUAL)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"orientacao\":\"BISSEXUAL\",\"descricaoOrientacao\":null,"
                        + "\"mostrarOrientacao\":false,\"consentidaEm\":\""
                        + agora
                        + "\"}",
                    JsonCompareMode.STRICT));

    assertThat(consentidaEm(conta)).isEqualTo(agora);
  }

  @Test
  void prefiroDescreverExigeTexto() throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(conta, "{\"orientacao\":\"OUTRA\",\"mostrarOrientacao\":true,\"consentimento\":true}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("IDENTIDADE_INVALIDA"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void prefiroDescreverGravaADescricao() throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(
            conta,
            "{\"orientacao\":\"OUTRA\",\"descricaoOrientacao\":\"sapatão\","
                + "\"mostrarOrientacao\":true,\"consentimento\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orientacao").value("OUTRA"))
        .andExpect(jsonPath("$.descricaoOrientacao").value("sapatão"));
  }

  @Test
  void orientacaoAusente422() throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(conta, "{\"mostrarOrientacao\":true,\"consentimento\":true}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("IDENTIDADE_INVALIDA"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void orientacaoDesconhecida400() throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(conta, BISSEXUAL.replace("BISSEXUAL", "POLIAMOROSA"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void mostrarGravado() throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(conta, BISSEXUAL.replace("false", "true")).andExpect(status().isOk());

    mvc.perform(get("/api/perfil/orientacao").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.mostrarOrientacao").value(true));
    assertThat(
            jdbc.sql("select mostrar from orientacao where conta_id = :conta")
                .param("conta", conta.id().valor())
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  void apagarLimpaTudo() throws Exception {
    ContaDeTeste conta = comPerfil();
    salvar(conta, BISSEXUAL).andExpect(status().isOk());

    apagar(conta).andExpect(status().isNoContent());

    assertThat(linhas(conta)).isZero();
    mvc.perform(get("/api/perfil/orientacao").cookie(cookieDe(conta)))
        .andExpect(
            content()
                .json(
                    "{\"orientacao\":null,\"descricaoOrientacao\":null,"
                        + "\"mostrarOrientacao\":false,\"consentidaEm\":null}",
                    JsonCompareMode.STRICT));
    apagar(conta).andExpect(status().isNoContent());
  }

  @Test
  void semPerfil422() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, "{\"consentimento\":false}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PERFIL_NECESSARIO"));
  }

  @Test
  void pronomesNaoMudamAoApagarOrientacao() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    mvc.perform(
            put("/api/perfil")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"versao\":null,\"apelido\":\"Bia\",\"pronomes\":\"OUTROS\","
                        + "\"descricaoPronomes\":\"ela/elu\",\"intencoes\":[\"AMIZADE\"]}")
                .cookie(cookieDe(conta)))
        .andExpect(status().isOk());
    salvar(conta, BISSEXUAL).andExpect(status().isOk());

    apagar(conta).andExpect(status().isNoContent());

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.versao").value(1))
        .andExpect(jsonPath("$.pronomes").value("OUTROS"))
        .andExpect(jsonPath("$.descricaoPronomes").value("ela/elu"));
  }

  @Test
  void outraContaNaoLe() throws Exception {
    ContaDeTeste dona = comPerfil();
    salvar(dona, BISSEXUAL).andExpect(status().isOk());
    ContaDeTeste outra = comPerfil();

    mvc.perform(get("/api/perfil/orientacao").cookie(cookieDe(outra)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orientacao").isEmpty());
  }

  @Test
  void exclusaoApagaOrientacao() throws Exception {
    ContaDeTeste conta = comPerfil();
    salvar(conta, BISSEXUAL).andExpect(status().isOk());

    exclusao.excluir(conta.id(), true);

    assertThat(linhas(conta)).isZero();
  }

  private ContaDeTeste comPerfil() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    mvc.perform(
            put("/api/perfil")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versao\":null,\"apelido\":\"Bia\",\"intencoes\":[\"AMIZADE\"]}")
                .cookie(cookieDe(conta)))
        .andExpect(status().isOk());
    return conta;
  }

  private ResultActions salvar(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        put("/api/perfil/orientacao")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private ResultActions apagar(ContaDeTeste conta) throws Exception {
    return mvc.perform(
        delete("/api/perfil/orientacao")
            .header(MesmaOrigem.CABECALHO, "1")
            .cookie(cookieDe(conta)));
  }

  private Instant consentidaEm(ContaDeTeste conta) {
    return jdbc.sql("select consentida_em from orientacao where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query((rs, linha) -> rs.getObject("consentida_em", OffsetDateTime.class).toInstant())
        .single();
  }

  private long linhas(ContaDeTeste conta) {
    return jdbc.sql("select count(*) from orientacao where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query(Long.class)
        .single();
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
