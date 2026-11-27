package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Com o formato ECS de produção, a linha de log da requisição é JSON e carrega o correlationId.
 *
 * <p>O Logback é global na JVM e o Spring Boot não o reconfigura se outro contexto em cache já o
 * iniciou; por isso o teste força a reconfiguração antes e devolve o log em texto depois, para não
 * depender da ordem da suíte nem deixar os testes seguintes em JSON.
 */
@SpringBootTest(properties = "logging.structured.format.console=ecs")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class FormatoDoLogTest {

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;

  @BeforeAll
  static void permitirQueOContextoReconfigureOLog() {
    sistemaDeLog().cleanUp();
  }

  @AfterAll
  static void voltarAoLogEmTexto() {
    LoggingSystem sistema = sistemaDeLog();
    System.clearProperty(
        LoggingSystemProperty.CONSOLE_STRUCTURED_FORMAT.getEnvironmentVariableName());
    sistema.cleanUp();
    sistema.beforeInitialize();
    sistema.initialize(new LoggingInitializationContext(new StandardEnvironment()), null, null);
  }

  @Test
  void linhaDaRequisicaoEJsonComCorrelationId(CapturedOutput saida) throws Exception {
    mvc.perform(get("/teste/log").header(IdDeCorrelacao.CABECALHO, "teste-1234-abcd"))
        .andExpect(status().isOk());

    String linha =
        saida
            .getOut()
            .lines()
            .filter(texto -> texto.contains(ControllerDeTesteDeLog.MENSAGEM))
            .findFirst()
            .orElseThrow();
    JsonNode registro = json.readTree(linha);

    assertThat(registro.path("message").asString()).isEqualTo(ControllerDeTesteDeLog.MENSAGEM);
    assertThat(registro.path("correlationId").asString()).isEqualTo("teste-1234-abcd");
  }

  private static LoggingSystem sistemaDeLog() {
    return LoggingSystem.get(FormatoDoLogTest.class.getClassLoader());
  }
}
