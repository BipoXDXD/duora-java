package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** A pausa tira a pessoa da fila sem mexer no perfil nem na conta. */
class PausaApiTest extends BancoDeTeste {

  @Autowired private ContasDeTeste contas;

  @Test
  void pausarApareceNoGet() throws Exception {
    ContaDeTeste conta = comPerfil();

    pausar(conta).andExpect(status().isNoContent());

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.pausada").value(true));
  }

  @Test
  void perfilSemPausaApareceComoNaoPausado() throws Exception {
    ContaDeTeste conta = comPerfil();

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.pausada").value(false));
  }

  @Test
  void pausarDuasVezesMantemOPrimeiroInstante() throws Exception {
    ContaDeTeste conta = comPerfil();
    pausar(conta).andExpect(status().isNoContent());
    relogio.avancar(Duration.ofHours(1));

    pausar(conta).andExpect(status().isNoContent());

    assertThat(pausadaEm(conta)).isEqualTo(INICIO);
  }

  @Test
  void voltarAAparecer() throws Exception {
    ContaDeTeste conta = comPerfil();
    pausar(conta).andExpect(status().isNoContent());

    retomar(conta).andExpect(status().isNoContent());

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.pausada").value(false));
  }

  @Test
  void retomarSemPausa204() throws Exception {
    ContaDeTeste conta = comPerfil();

    retomar(conta).andExpect(status().isNoContent());
  }

  @Test
  void pausaNaoMudaAVersao() throws Exception {
    ContaDeTeste conta = comPerfil();

    pausar(conta).andExpect(status().isNoContent());
    retomar(conta).andExpect(status().isNoContent());

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.versao").value(1));
  }

  @Test
  void pausaSemPerfil422() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    pausar(conta)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PERFIL_NECESSARIO"));
  }

  @Test
  void retomarSemPerfil422() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    retomar(conta)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PERFIL_NECESSARIO"));
  }

  @Test
  void pausaNaoMexeNaConta() throws Exception {
    ContaDeTeste conta = comPerfil();

    pausar(conta).andExpect(status().isNoContent());

    assertThat(
            jdbc.sql("select estado from conta where id = :conta")
                .param("conta", conta.id().valor())
                .query(String.class)
                .single())
        .isEqualTo("ATIVA");
    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.apelido").value("Bia"))
        .andExpect(jsonPath("$.bio").value("oi"));
  }

  private ContaDeTeste comPerfil() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    mvc.perform(
            put("/api/perfil")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"versao\":null,\"apelido\":\"Bia\",\"intencoes\":[\"AMIZADE\"],"
                        + "\"bio\":\"oi\"}")
                .cookie(cookieDe(conta)))
        .andExpect(status().isOk());
    return conta;
  }

  private ResultActions pausar(ContaDeTeste conta) throws Exception {
    return mvc.perform(
        post("/api/perfil/pausa").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)));
  }

  private ResultActions retomar(ContaDeTeste conta) throws Exception {
    return mvc.perform(
        delete("/api/perfil/pausa").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)));
  }

  private Instant pausadaEm(ContaDeTeste conta) {
    return jdbc.sql("select pausada_em from perfil where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query((rs, linha) -> rs.getObject("pausada_em", OffsetDateTime.class).toInstant())
        .single();
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
