package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;

class PerfilApiTest extends BancoDeTeste {

  private static final String CRIAR_BIA =
      "{\"versao\":null,\"apelido\":\"Bia\",\"intencoes\":[\"AMIZADE\"],\"bio\":\"oi\"}";

  private static final String SEM_FOTO =
      "\"foto\":{\"aprovada\":null,\"pendente\":null,\"recusadaEm\":null}";

  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private FotosDeTeste fotos;

  @Test
  void perfilNovoComecaVazio() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"versao\":null,\"apelido\":null,\"pronomes\":null,"
                        + "\"descricaoPronomes\":null,\"intencoes\":[],\"bio\":\"\","
                        + "\"pausada\":false,\"prontoParaFila\":false,\"pendencias\":[\"PERFIL\"],"
                        + SEM_FOTO
                        + "}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void criarDaVersao1() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, CRIAR_BIA)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"versao\":1,\"apelido\":\"Bia\",\"pronomes\":null,"
                        + "\"descricaoPronomes\":null,\"intencoes\":[\"AMIZADE\"],\"bio\":\"oi\","
                        + "\"pausada\":false,\"prontoParaFila\":true,\"pendencias\":[],"
                        + SEM_FOTO
                        + "}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void editarSobeAVersao() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    salvar(conta, CRIAR_BIA).andExpect(status().isOk());

    salvar(conta, edicao(1, "Bea"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versao").value(2))
        .andExpect(jsonPath("$.apelido").value("Bea"));

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(jsonPath("$.versao").value(2))
        .andExpect(jsonPath("$.apelido").value("Bea"));
  }

  @Test
  void edicaoConcorrenteRecusada() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    salvar(conta, CRIAR_BIA).andExpect(status().isOk());
    salvar(conta, edicao(1, "Primeira")).andExpect(status().isOk());

    salvar(conta, edicao(1, "Segunda"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERSAO_OBSOLETA"))
        .andExpect(
            jsonPath("$.mensagem")
                .value("Seu perfil mudou enquanto você editava. Confira e salve de novo."));

    assertThat(linhaDe(conta)).containsEntry("apelido", "Primeira").containsEntry("versao", 2L);
  }

  @Test
  void versaoNulaComPerfilExistente409() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    salvar(conta, CRIAR_BIA).andExpect(status().isOk());

    salvar(conta, CRIAR_BIA.replace("Bia", "Outra"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERSAO_OBSOLETA"));

    assertThat(linhaDe(conta)).containsEntry("apelido", "Bia").containsEntry("versao", 1L);
  }

  @Test
  void versaoComPerfilInexistente409() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, edicao(1, "Bia"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERSAO_OBSOLETA"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void campoExtraRecusado() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, CRIAR_BIA.replace("{", "{\"contaId\":\"" + conta.id() + "\","))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void intencaoDesconhecida400() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, CRIAR_BIA.replace("[\"AMIZADE\"]", "[\"CASUAL\"]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void intencoesVaziasRecusadas() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, CRIAR_BIA.replace("[\"AMIZADE\"]", "[]"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("INTENCAO_INVALIDA"))
        .andExpect(jsonPath("$.mensagem").value("Escolha amizade, namoro ou os dois."));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void apelidoAusente422() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, "{\"versao\":null,\"intencoes\":[\"AMIZADE\"]}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("APELIDO_INVALIDO"))
        .andExpect(jsonPath("$.mensagem").value("O apelido precisa ter de 1 a 20 caracteres."));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void bioAusenteViraVazia() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, "{\"versao\":null,\"apelido\":\"Bia\",\"intencoes\":[\"AMIZADE\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bio").value(""));

    assertThat(linhaDe(conta)).containsEntry("bio", "");
  }

  @Test
  void apelidoGravadoSemEspacos() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, CRIAR_BIA.replace("\"Bia\"", "\"  Bia  \"")).andExpect(status().isOk());

    assertThat(linhaDe(conta)).containsEntry("apelido", "Bia");
  }

  @Test
  void intencoesNaOrdemDoEnum() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, CRIAR_BIA.replace("[\"AMIZADE\"]", "[\"NAMORO\",\"AMIZADE\"]"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"intencoes\":[\"AMIZADE\",\"NAMORO\"]}"));

    assertThat(linhaDe(conta))
        .containsEntry("quer_amizade", true)
        .containsEntry("quer_namoro", true);
  }

  @Test
  void pronomesGravadosEDevolvidos() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, CRIAR_BIA.replace("{", "{\"pronomes\":\"ELU_DELU\","))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pronomes").value("ELU_DELU"));

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(content().json("{\"pronomes\":\"ELU_DELU\",\"descricaoPronomes\":null}"));
    assertThat(linhaDe(conta)).containsEntry("pronomes", "ELU_DELU");
  }

  @Test
  void pronomesOutrosSemDescricao422() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    salvar(conta, CRIAR_BIA.replace("{", "{\"pronomes\":\"OUTROS\","))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("IDENTIDADE_INVALIDA"));

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void semSessao401() throws Exception {
    mvc.perform(get("/api/perfil"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  @Test
  void contaAguardandoRecebe403() throws Exception {
    ContaDeTeste conta =
        contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, LocalDate.of(1990, 5, 10));

    mvc.perform(get("/api/perfil").cookie(cookieDe(conta)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CADASTRO_INCOMPLETO"));
  }

  @Test
  void exclusaoDaContaApagaPerfil() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    salvar(conta, CRIAR_BIA).andExpect(status().isOk());

    exclusao.excluir(conta.id(), true);

    assertThat(linhas(conta)).isZero();
  }

  @Test
  void getTrazPendencias() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    consultar(conta)
        .andExpect(jsonPath("$.pendencias").value(contains("PERFIL")))
        .andExpect(jsonPath("$.prontoParaFila").value(false));

    salvar(conta, CRIAR_BIA).andExpect(status().isOk());

    consultar(conta)
        .andExpect(jsonPath("$.pendencias").isEmpty())
        .andExpect(jsonPath("$.prontoParaFila").value(true));
  }

  @Test
  void getTrazFotoDoTitular() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    salvar(conta, CRIAR_BIA).andExpect(status().isOk());
    fotos.enviar(conta.id());

    consultar(conta)
        .andExpect(jsonPath("$.foto.pendente.nitida").value(endsWith("/nitida")))
        .andExpect(jsonPath("$.foto.aprovada").value(nullValue()));
  }

  @Test
  void fotoEPausaNaoMudamAVersao() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    salvar(conta, CRIAR_BIA).andExpect(status().isOk());

    fotos.enviar(conta.id());
    mvc.perform(
            post("/api/perfil/pausa").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete("/api/perfil/pausa").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)))
        .andExpect(status().isNoContent());

    consultar(conta).andExpect(jsonPath("$.versao").value(1));
  }

  private ResultActions consultar(ContaDeTeste conta) throws Exception {
    return mvc.perform(get("/api/perfil").cookie(cookieDe(conta))).andExpect(status().isOk());
  }

  private ResultActions salvar(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        put("/api/perfil")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private static String edicao(long versao, String apelido) {
    return "{\"versao\":"
        + versao
        + ",\"apelido\":\""
        + apelido
        + "\",\"intencoes\":[\"AMIZADE\"],\"bio\":\"oi\"}";
  }

  private Map<String, Object> linhaDe(ContaDeTeste conta) {
    return jdbc.sql(
            """
            select apelido, pronomes, descricao_pronomes, quer_amizade, quer_namoro, bio, versao
            from perfil where conta_id = :conta
            """)
        .param("conta", conta.id().valor())
        .query()
        .singleRow();
  }

  private long linhas(ContaDeTeste conta) {
    return jdbc.sql("select count(*) from perfil where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query(Long.class)
        .single();
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
