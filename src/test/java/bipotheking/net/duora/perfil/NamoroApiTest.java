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
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;

/** Gênero e interesses: dado sensível, só com consentimento e só para quem marcou Namoro. */
class NamoroApiTest extends BancoDeTeste {

  private static final String MULHER_BUSCA_HOMEM_E_OUTRO =
      "{\"genero\":\"MULHER\",\"interesses\":[\"HOMEM\",\"OUTRO\"],\"consentimento\":true}";
  private static final String AMIZADE_E_NAMORO = "[\"AMIZADE\",\"NAMORO\"]";
  private static final String SO_AMIZADE = "[\"AMIZADE\"]";

  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;

  @Test
  void gravaComConsentimento() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);
    relogio.avancar(Duration.ofMinutes(5));
    Instant agora = INICIO.plus(Duration.ofMinutes(5));

    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"genero\":\"MULHER\",\"descricaoGenero\":null,"
                        + "\"interesses\":[\"HOMEM\",\"OUTRO\"],\"consentidoEm\":\""
                        + agora
                        + "\"}",
                    JsonCompareMode.STRICT));

    assertThat(interesses(conta)).containsExactly("HOMEM", "OUTRO");
    assertThat(consentidoEm(conta)).isEqualTo(agora);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"genero\":\"MULHER\",\"interesses\":[\"HOMEM\"],\"consentimento\":false}",
        "{\"genero\":\"MULHER\",\"interesses\":[\"HOMEM\"]}"
      })
  void semConsentimentoRecusado(String corpo) throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);

    salvar(conta, corpo)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONSENTIMENTO_NECESSARIO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void intencaoAmizadeRecusada() throws Exception {
    ContaDeTeste conta = comPerfil(SO_AMIZADE);

    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("INTENCAO_SEM_NAMORO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void semPerfilVemAntesDosOutrosErros() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, "{\"interesses\":[],\"consentimento\":false}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PERFIL_NECESSARIO"));
  }

  @Test
  void generoAusente422() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);

    salvar(conta, "{\"interesses\":[\"HOMEM\"],\"consentimento\":true}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("GENERO_INVALIDO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void descricaoDe41Recusada() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);

    salvar(
            conta,
            "{\"genero\":\"OUTRO\",\"descricaoGenero\":\""
                + "a".repeat(41)
                + "\",\"interesses\":[\"HOMEM\"],\"consentimento\":true}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("GENERO_INVALIDO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void generoGranularRecusado() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);

    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO.replace("\"MULHER\"", "\"MULHER_TRANS\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void getSemDadosDevolveNulos() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);

    mvc.perform(get("/api/perfil/namoro").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"genero\":null,\"descricaoGenero\":null,\"interesses\":[],"
                        + "\"consentidoEm\":null}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void trocarParaAmizadeApagaDados() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);
    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO).andExpect(status().isOk());

    salvarPerfil(conta, "1", SO_AMIZADE).andExpect(status().isOk());

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void revogarApagaEMudaIntencao() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);
    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO).andExpect(status().isOk());

    revogar(conta).andExpect(status().isNoContent());

    assertThat(linhas(conta)).isZero();
    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(content().json("{\"versao\":2,\"intencoes\":[\"AMIZADE\"]}"));
  }

  @Test
  void revogarSemPerfil204() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    revogar(conta).andExpect(status().isNoContent());
    revogar(conta).andExpect(status().isNoContent());
  }

  @Test
  void religarNamoroExigeNovoConsentimento() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);
    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO).andExpect(status().isOk());
    salvarPerfil(conta, "1", SO_AMIZADE).andExpect(status().isOk());
    salvarPerfil(conta, "2", AMIZADE_E_NAMORO).andExpect(status().isOk());

    mvc.perform(get("/api/perfil/namoro").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.genero").isEmpty())
        .andExpect(jsonPath("$.consentidoEm").isEmpty());
    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO.replace("true", "false"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONSENTIMENTO_NECESSARIO"));
  }

  @Test
  void bancoRecusaNamoroSemIntencao() throws Exception {
    ContaDeTeste conta = comPerfil(SO_AMIZADE);

    assertThatThrownBy(() ->
            jdbc.sql(
                    """
                    insert into namoro (conta_id, genero, interesses, consentido_em)
                    values (:conta, 'MULHER', array['HOMEM'], now())
                    """)
                .param("conta", conta.id().valor())
                .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void bancoRecusaDesligarComDados() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);
    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO).andExpect(status().isOk());

    assertThatThrownBy(() ->
            jdbc.sql("update perfil set quer_namoro = false where conta_id = :conta")
                .param("conta", conta.id().valor())
                .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void outraContaNaoLe() throws Exception {
    ContaDeTeste dona = comPerfil(AMIZADE_E_NAMORO);
    salvar(dona, MULHER_BUSCA_HOMEM_E_OUTRO).andExpect(status().isOk());
    ContaDeTeste outra = comPerfil(AMIZADE_E_NAMORO);

    mvc.perform(get("/api/perfil/namoro").cookie(cookieDe(outra)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.genero").isEmpty())
        .andExpect(jsonPath("$.interesses").isEmpty());
  }

  @Test
  void exclusaoDaContaApagaPreferenciasENamoro() throws Exception {
    ContaDeTeste conta = comPerfil(AMIZADE_E_NAMORO);
    salvar(conta, MULHER_BUSCA_HOMEM_E_OUTRO).andExpect(status().isOk());
    mvc.perform(
            put("/api/perfil/preferencias")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"versao\":null,\"idadeMinima\":18,\"idadeMaxima\":99,"
                        + "\"raio\":\"SEM_LIMITE\",\"raioObrigatorio\":false}")
                .cookie(cookieDe(conta)))
        .andExpect(status().isOk());

    exclusao.excluir(conta.id(), true);

    assertThat(linhas(conta)).isZero();
    assertThat(
            jdbc.sql("select count(*) from preferencias where conta_id = :conta")
                .param("conta", conta.id().valor())
                .query(Long.class)
                .single())
        .isZero();
  }

  private ContaDeTeste comPerfil(String intencoes) throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    salvarPerfil(conta, "null", intencoes).andExpect(status().isOk());
    return conta;
  }

  private ResultActions salvarPerfil(ContaDeTeste conta, String versao, String intencoes)
      throws Exception {
    return mvc.perform(
        put("/api/perfil")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"versao\":" + versao + ",\"apelido\":\"Bia\",\"intencoes\":" + intencoes + "}")
            .cookie(cookieDe(conta)));
  }

  private ResultActions salvar(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        put("/api/perfil/namoro")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private ResultActions revogar(ContaDeTeste conta) throws Exception {
    return mvc.perform(
        delete("/api/perfil/namoro").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)));
  }

  private List<String> interesses(ContaDeTeste conta) {
    return jdbc.sql("select interesses from namoro where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query((rs, linha) -> Arrays.asList((String[]) rs.getArray("interesses").getArray()))
        .single();
  }

  private Instant consentidoEm(ContaDeTeste conta) {
    return jdbc.sql("select consentido_em from namoro where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query((rs, linha) -> rs.getObject("consentido_em", OffsetDateTime.class).toInstant())
        .single();
  }

  private long linhas(ContaDeTeste conta) {
    return jdbc.sql("select count(*) from namoro where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query(Long.class)
        .single();
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
