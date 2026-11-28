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
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;

/** Os ajustes de busca: faixa etária e raio, com o raio preso à localização ativa. */
class PreferenciasApiTest extends BancoDeTeste {

  private static final String SAO_PAULO = "{\"latitude\":-23.5505,\"longitude\":-46.6333}";

  @Autowired private ContasDeTeste contas;

  @Test
  void padroesSemLinha() throws Exception {
    ContaDeTeste conta = comPerfil();

    mvc.perform(get("/api/perfil/preferencias").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"versao\":null,\"idadeMinima\":18,\"idadeMaxima\":99,"
                        + "\"raio\":\"SEM_LIMITE\",\"raioObrigatorio\":false}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void semPerfil422() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, ajustes(null, 18, 99, "SEM_LIMITE", false))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PERFIL_NECESSARIO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void raioSemLocalizacaoRecusado() throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(conta, ajustes(null, 18, 99, "ATE_5_KM", true))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("LOCALIZACAO_NECESSARIA"))
        .andExpect(jsonPath("$.mensagem").value("Ative a localização para usar um raio."));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void raioComLocalizacaoAceito() throws Exception {
    ContaDeTeste conta = comPerfil();
    ativarLocalizacao(conta);
    assertThat(celula(conta)).isEqualTo("6gyf4");

    salvar(conta, ajustes(null, 25, 40, "ATE_5_KM", true))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"versao\":1,\"idadeMinima\":25,\"idadeMaxima\":40,"
                        + "\"raio\":\"ATE_5_KM\",\"raioObrigatorio\":true}",
                    JsonCompareMode.STRICT));

    assertThat(linhaDe(conta))
        .containsEntry("raio", "ATE_5_KM")
        .containsEntry("raio_obrigatorio", true)
        .containsEntry("versao", 1L);
  }

  @Test
  void semLimiteZeraObrigatorio() throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(conta, ajustes(null, 18, 99, "SEM_LIMITE", true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.raioObrigatorio").value(false));

    assertThat(linhaDe(conta)).containsEntry("raio_obrigatorio", false);
  }

  @Test
  void faixaInvertida422() throws Exception {
    ContaDeTeste conta = comPerfil();

    salvar(conta, ajustes(null, 40, 30, "SEM_LIMITE", false))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("FAIXA_ETARIA_INVALIDA"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void edicaoConcorrenteRecusada() throws Exception {
    ContaDeTeste conta = comPerfil();
    salvar(conta, ajustes(null, 18, 99, "SEM_LIMITE", false)).andExpect(status().isOk());
    salvar(conta, ajustes(1L, 20, 30, "SEM_LIMITE", false)).andExpect(status().isOk());

    salvar(conta, ajustes(1L, 40, 50, "SEM_LIMITE", false))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERSAO_OBSOLETA"));

    assertThat(linhaDe(conta)).containsEntry("idade_minima", 20).containsEntry("versao", 2L);
  }

  @Test
  void revogarLocalizacaoVoltaParaSemLimite() throws Exception {
    ContaDeTeste conta = comPerfil();
    ativarLocalizacao(conta);
    salvar(conta, ajustes(null, 18, 99, "ATE_5_KM", true)).andExpect(status().isOk());

    revogarLocalizacao(conta);

    mvc.perform(get("/api/perfil/preferencias").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"versao\":2,\"idadeMinima\":18,\"idadeMaxima\":99,"
                        + "\"raio\":\"SEM_LIMITE\",\"raioObrigatorio\":false}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void formularioAbertoDepoisDaRevogacao409() throws Exception {
    ContaDeTeste conta = comPerfil();
    ativarLocalizacao(conta);
    salvar(conta, ajustes(null, 18, 99, "ATE_5_KM", true)).andExpect(status().isOk());
    revogarLocalizacao(conta);

    salvar(conta, ajustes(1L, 18, 99, "ATE_5_KM", false))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERSAO_OBSOLETA"));

    assertThat(linhaDe(conta)).containsEntry("raio", "SEM_LIMITE").containsEntry("versao", 2L);
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

  private void ativarLocalizacao(ContaDeTeste conta) throws Exception {
    mvc.perform(
            put("/api/perfil/localizacao")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SAO_PAULO)
                .cookie(cookieDe(conta)))
        .andExpect(status().isNoContent());
  }

  private void revogarLocalizacao(ContaDeTeste conta) throws Exception {
    mvc.perform(
            delete("/api/perfil/localizacao")
                .header(MesmaOrigem.CABECALHO, "1")
                .cookie(cookieDe(conta)))
        .andExpect(status().isNoContent());
  }

  private ResultActions salvar(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        put("/api/perfil/preferencias")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private static String ajustes(
      @Nullable Long versao, int minima, int maxima, String raio, boolean obrigatorio) {
    return "{\"versao\":%s,\"idadeMinima\":%d,\"idadeMaxima\":%d,\"raio\":\"%s\",\"raioObrigatorio\":%b}"
        .formatted(versao, minima, maxima, raio, obrigatorio);
  }

  private String celula(ContaDeTeste conta) {
    return jdbc.sql("select celula from localizacao where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query(String.class)
        .single();
  }

  private Map<String, Object> linhaDe(ContaDeTeste conta) {
    return jdbc.sql(
            """
            select idade_minima, idade_maxima, raio, raio_obrigatorio, versao
            from preferencias where conta_id = :conta
            """)
        .param("conta", conta.id().valor())
        .query()
        .singleRow();
  }

  private long linhas(ContaDeTeste conta) {
    return jdbc.sql("select count(*) from preferencias where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query(Long.class)
        .single();
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
