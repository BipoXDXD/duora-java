package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.DadosDeNamoro;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.web.BancoDeTeste;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /api/perfis/{contaId}}: o perfil público de outra conta. Toda recusa é o mesmo 404; o
 * {@code instance} do problem+json ecoa o caminho pedido, então os corpos são comparados sem ele.
 *
 * <p>O {@link AcessoAFoto} é dublê: por padrão decide como o {@link AcessoAFotoPadrao}, e um teste
 * o força a {@code NENHUM}.
 */
class PerfisApiTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);

  @MockitoBean private AcessoAFoto acesso;

  @Autowired private ConsultaDeContas consulta;
  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private ServicoDePerfil servicoDePerfil;
  @Autowired private ServicoDeNamoro servicoDeNamoro;

  @BeforeEach
  void decidirComoOPadrao() {
    AcessoAFotoPadrao padrao = new AcessoAFotoPadrao(consulta);
    when(acesso.nivel(any(), any()))
        .thenAnswer(chamada -> padrao.nivel(chamada.getArgument(0), chamada.getArgument(1)));
  }

  @Test
  void terceiroVePerfilPublico() throws Exception {
    ContaDeTeste titular = contas.criar("Ana", NASCIMENTO);
    ContaDeTeste terceiro = contas.criarAtiva();

    pedir(terceiro, titular.id().valor().toString())
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"apelido\":\"Ana\",\"foto\":null,\"pronomes\":null,\"verificado\":true}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void perfilPublicoNuncaTrazBioNemNamoro() throws Exception {
    ContaDeTeste titular = contas.criarVerificada();
    servicoDePerfil.salvar(
        titular.id(),
        null,
        new EdicaoDoPerfil(
            Apelido.de("Ana"),
            Optional.empty(),
            new Intencoes(EnumSet.of(Intencao.NAMORO)),
            Bio.de("canario-bio-7f3")));
    servicoDeNamoro.salvar(
        titular.id(), DadosDeNamoro.de(Genero.MULHER, null, Set.of(Genero.HOMEM)), true);
    ContaDeTeste terceiro = contas.criarAtiva();

    String corpo =
        corpo(
            resposta(pedir(terceiro, titular.id().valor().toString()).andExpect(status().isOk())));

    assertThat(corpo)
        .doesNotContain("canario-bio-7f3")
        .doesNotContain("genero")
        .doesNotContain("MULHER")
        .doesNotContain("HOMEM");
  }

  @Test
  void contaExcluida404() throws Exception {
    ContaDeTeste titular = contas.criar("Ana", NASCIMENTO);
    exclusao.excluir(titular.id(), true);

    assertIgualAInexistente(titular.id());
  }

  @Test
  void contaSuspensa404() throws Exception {
    ContaDeTeste titular = contas.criar(EstadoConta.SUSPENSA, NASCIMENTO);
    servicoDePerfil.salvar(
        titular.id(),
        null,
        new EdicaoDoPerfil(Apelido.de("Ana"), Optional.empty(), Intencoes.SO_AMIZADE, Bio.de("")));

    assertIgualAInexistente(titular.id());
  }

  @Test
  void semPerfil404() throws Exception {
    ContaDeTeste titular = contas.criarVerificada();

    assertIgualAInexistente(titular.id());
  }

  @Test
  void acessoNenhum404IgualAInexistente() throws Exception {
    ContaDeTeste titular = contas.criar("Ana", NASCIMENTO);
    doReturn(NivelDeAcesso.NENHUM).when(acesso).nivel(any(), any());

    assertIgualAInexistente(titular.id());
  }

  @Test
  void idMalformado400() throws Exception {
    ContaDeTeste terceiro = contas.criarAtiva();

    pedir(terceiro, "nao-e-uuid")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
  }

  @Test
  void semSessao401() throws Exception {
    ContaDeTeste titular = contas.criar("Ana", NASCIMENTO);

    mvc.perform(get("/api/perfis/" + titular.id().valor()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  /**
   * Mesmo status, tipo e corpo (sem {@code instance}) que o de uma conta que não existe, e o id
   * pedido fora de {@code detail}, {@code mensagem}, {@code codigo} e {@code title}.
   */
  private void assertIgualAInexistente(ContaId titular) throws Exception {
    ContaDeTeste terceiro = contas.criarAtiva();
    String id = titular.valor().toString();

    MockHttpServletResponse recusada =
        resposta(
            pedir(terceiro, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERFIL_INEXISTENTE"))
                .andExpect(jsonPath("$.mensagem").value("Não encontramos este perfil."))
                .andExpect(jsonPath("$.detail").value(semOId(id)))
                .andExpect(jsonPath("$.mensagem").value(semOId(id)))
                .andExpect(jsonPath("$.codigo").value(semOId(id)))
                .andExpect(jsonPath("$.title").value(semOId(id))));
    MockHttpServletResponse inexistente = resposta(pedir(terceiro, UUID.randomUUID().toString()));

    assertThat(recusada.getStatus()).isEqualTo(inexistente.getStatus());
    assertThat(recusada.getContentType()).isEqualTo(inexistente.getContentType());
    assertThat(semInstance(recusada)).isEqualTo(semInstance(inexistente));
  }

  private ResultActions pedir(ContaDeTeste quemVe, String contaId) throws Exception {
    return mvc.perform(get("/api/perfis/" + contaId).cookie(cookieDe(quemVe)));
  }

  private static MockHttpServletResponse resposta(ResultActions acoes) {
    return acoes.andReturn().getResponse();
  }

  private static String corpo(MockHttpServletResponse resposta) throws Exception {
    return resposta.getContentAsString(StandardCharsets.UTF_8);
  }

  /** O corpo com o valor de {@code instance} esvaziado; o resto, byte a byte. */
  private static String semInstance(MockHttpServletResponse resposta) throws Exception {
    return corpo(resposta).replaceFirst("\"instance\":\"[^\"]*\"", "\"instance\":\"\"");
  }

  private static Matcher<String> semOId(String id) {
    return not(containsString(id));
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
