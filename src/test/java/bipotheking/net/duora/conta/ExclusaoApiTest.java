package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.GanchoDeExclusaoDeTeste.Chamada;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeAceites;
import bipotheking.net.duora.conta.dominio.Token;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.notificacao.infra.DespachanteDaOutbox;
import bipotheking.net.duora.notificacao.infra.EmailNoLog;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ExclusaoApiTest extends BancoDeTeste {

  private static final String CONFIRMO = "{\"confirmo\":true}";
  private static final String COOKIE_EXPIRADO = "Max-Age=0";
  private static final Pattern TOKEN_DO_LINK = Pattern.compile("#t=([A-Za-z0-9_-]+)");

  @Autowired private ContasDeTeste contas;
  @Autowired private GanchoDeExclusaoDeTeste gancho;
  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private RepositorioDeAceites aceites;
  @Autowired private DocumentosLegais documentos;
  @Autowired private Sessoes sessoes;
  @Autowired private PedidoDeEntrada pedidoDeEntrada;
  @Autowired private PlatformTransactionManager transacoes;
  @Autowired private RandomGenerator aleatorio;
  @Autowired private DespachanteDaOutbox despachante;
  @Autowired private EmailNoLog emailNoLog;

  @BeforeEach
  void esquecerOGancho() {
    gancho.esquecer();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"confirmo\":false}", "{\"confirmo\":null}", "{}"})
  void semConfirmacaoRecusa(String corpo) throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    excluir(conta, corpo)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONFIRMACAO_NECESSARIA"))
        .andExpect(
            jsonPath("$.mensagem").value("Confirme que entende que a exclusão apaga seus dados."));

    assertThat(linhaDa(conta).get("estado")).isEqualTo("ATIVA");
    assertThat(linhaDa(conta).get("email")).isEqualTo(emailDe(conta));
    assertThat(sessoesDa(conta)).isOne();
    assertThat(gancho.chamadas()).isEmpty();
  }

  @Test
  void excluiDadosPessoais() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    sessoes.abrir(conta.id(), OrigemDeTeste.LOCAL);
    aceitar(conta);
    pedirLink(emailDe(conta));
    Instant agora = INICIO.plusSeconds(90);
    relogio.ajustar(agora);

    excluir(conta, CONFIRMO)
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE_EXPIRADO)));

    Map<String, Object> linha = linhaDa(conta);
    assertThat(linha.get("estado")).isEqualTo("EXCLUIDA");
    assertThat(linha.get("email")).isNull();
    assertThat(linha.get("data_nascimento")).isNull();
    assertThat(excluidaEm(conta)).isEqualTo(agora);
    assertThat(sessoesDa(conta)).isZero();
    assertThat(aceitesDa(conta)).isZero();
    assertThat(linksDo(emailDe(conta))).isZero();
    excluir(conta, CONFIRMO)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  @Test
  void mensagemPendenteDaContaPerdeACarga() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    pedirLink(emailDe(conta));

    excluir(conta, CONFIRMO).andExpect(status().isNoContent());

    assertThat(despachante.despacharAgora()).isZero();
    assertThat(emailNoLog.ultimaPara(emailDe(conta))).isEmpty();
    Map<String, Object> mensagem =
        jdbc.sql("select carga, falhou_em, ultimo_erro from outbox").query().singleRow();
    assertThat(mensagem.get("carga")).isNull();
    assertThat(mensagem.get("falhou_em")).isNotNull();
    assertThat(mensagem.get("ultimo_erro")).isEqualTo("titular-esquecido");
  }

  @Test
  void ganchoRodaNaExclusao() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    excluir(conta, CONFIRMO).andExpect(status().isNoContent());

    assertThat(gancho.chamadas()).containsExactly(new Chamada(conta.id(), true));
  }

  @Test
  void falhaNoGanchoDesfazTudo() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    aceitar(conta);
    pedirLink(emailDe(conta));
    gancho.falharNaProxima();

    excluir(conta, CONFIRMO).andExpect(status().isInternalServerError());

    Map<String, Object> linha = linhaDa(conta);
    assertThat(linha.get("estado")).isEqualTo("ATIVA");
    assertThat(linha.get("email")).isEqualTo(emailDe(conta));
    assertThat(linha.get("excluida_em")).isNull();
    assertThat(sessoesDa(conta)).isOne();
    assertThat(aceitesDa(conta)).isEqualTo(2);
    assertThat(linksDo(emailDe(conta))).isOne();
    mvc.perform(get("/api/teste/so-ativa").cookie(cookieDe(conta))).andExpect(status().isOk());
  }

  @Test
  void mesmoEmailCriaContaNova() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    String email = emailDe(conta);
    excluir(conta, CONFIRMO).andExpect(status().isNoContent());

    mvc.perform(
            post("/api/conta/entrar")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"tokenVerificacao\":\"token-do-widget\"}")
                .with(OrigemDeTeste.ipAleatorio()))
        .andExpect(status().isAccepted());
    despachante.despacharAgora();
    mvc.perform(
            post("/api/conta/confirmar")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + tokenDaUltimaMensagemPara(email) + "\"}")
                .with(OrigemDeTeste.ipAleatorio()))
        .andExpect(status().isOk());

    Map<String, Object> nova =
        jdbc.sql("select id, estado from conta where email = :email")
            .param("email", email)
            .query()
            .singleRow();
    assertThat(nova.get("estado")).isEqualTo("PENDENTE");
    assertThat(nova.get("id")).isNotEqualTo(conta.id().valor());
  }

  @Test
  void outraContaIntacta() throws Exception {
    ContaDeTeste excluida = contas.criarAtiva();
    ContaDeTeste outra = contas.criarAtiva();
    aceitar(excluida);
    aceitar(outra);
    pedirLink(emailDe(excluida));
    pedirLink(emailDe(outra));

    excluir(excluida, CONFIRMO).andExpect(status().isNoContent());

    Map<String, Object> linha = linhaDa(outra);
    assertThat(linha.get("estado")).isEqualTo("ATIVA");
    assertThat(linha.get("email")).isEqualTo(emailDe(outra));
    assertThat(linha.get("data_nascimento")).isNotNull();
    assertThat(sessoesDa(outra)).isOne();
    assertThat(aceitesDa(outra)).isEqualTo(2);
    assertThat(linksDo(emailDe(outra))).isOne();
    assertThat(
            jdbc.sql("select count(*) from outbox where carga->>'para' = :email")
                .param("email", emailDe(outra))
                .query(Long.class)
                .single())
        .as("a mensagem da outra conta segue pendente")
        .isOne();
  }

  @Test
  void aguardandoTambemExclui() throws Exception {
    ContaDeTeste conta =
        contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, LocalDate.of(1990, 5, 10));

    excluir(conta, CONFIRMO).andExpect(status().isNoContent());

    assertThat(linhaDa(conta).get("estado")).isEqualTo("EXCLUIDA");
  }

  /**
   * O {@code @ContaAtual} já barra a conta banida, mas o banimento pode chegar entre o interceptor
   * e a transação. Excluir a banida apagaria o e-mail e o liberaria para uma conta nova (evasão de
   * banimento): o caso de uso recusa.
   */
  @Test
  void banidaNaoEhExcluida() {
    ContaDeTeste conta = contas.criar(EstadoConta.BANIDA, LocalDate.of(1990, 5, 10));

    assertThatThrownBy(() -> exclusao.excluir(conta.id(), true))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoSessao.CONTA_INDISPONIVEL));

    assertThat(linhaDa(conta).get("estado")).isEqualTo("BANIDA");
    assertThat(linhaDa(conta).get("email")).isEqualTo(emailDe(conta));
    assertThat(gancho.chamadas()).isEmpty();
  }

  private ResultActions excluir(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        post("/api/conta/exclusao")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private void aceitar(ContaDeTeste conta) {
    for (DocumentoLegal documento : DocumentoLegal.values()) {
      aceites.gravar(conta.id(), documento, documentos.atual(documento), INICIO);
    }
  }

  /** Grava o link como o pedido grava, com a mensagem pendente na outbox. */
  private void pedirLink(String email) {
    new TransactionTemplate(transacoes)
        .executeWithoutResult(
            status -> pedidoDeEntrada.registrarNaTransacao(Email.de(email), Token.novo(aleatorio)));
  }

  private String tokenDaUltimaMensagemPara(String email) {
    MensagemDeEmail mensagem = emailNoLog.ultimaPara(email).orElseThrow();
    Matcher token = TOKEN_DO_LINK.matcher(mensagem.texto());
    assertThat(token.find()).as("a mensagem traz o link").isTrue();
    return token.group(1);
  }

  private Map<String, Object> linhaDa(ContaDeTeste conta) {
    return jdbc.sql("select estado, email, data_nascimento, excluida_em from conta where id = :id")
        .param("id", conta.id().valor())
        .query()
        .singleRow();
  }

  private Instant excluidaEm(ContaDeTeste conta) {
    return jdbc.sql("select excluida_em from conta where id = :id")
        .param("id", conta.id().valor())
        .query((rs, linha) -> rs.getObject("excluida_em", OffsetDateTime.class).toInstant())
        .single();
  }

  private long sessoesDa(ContaDeTeste conta) {
    return contarDaConta("select count(*) from sessao where conta_id = :id", conta);
  }

  private long aceitesDa(ContaDeTeste conta) {
    return contarDaConta("select count(*) from aceite_documento where conta_id = :id", conta);
  }

  private long contarDaConta(String sql, ContaDeTeste conta) {
    return jdbc.sql(sql).param("id", conta.id().valor()).query(Long.class).single();
  }

  private long linksDo(String email) {
    return jdbc.sql("select count(*) from link_de_entrada where email = :email")
        .param("email", email)
        .query(Long.class)
        .single();
  }

  private static String emailDe(ContaDeTeste conta) {
    return conta.id().valor() + "@teste.duora.invalid";
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
