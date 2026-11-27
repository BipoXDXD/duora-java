package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.Token;
import bipotheking.net.duora.nucleo.Origem;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registros de acesso do Marco Civil (art. 15): IP, porta e instante de cada login e renovação de
 * sessão, guardados por 183 dias, mesmo depois da exclusão da conta, e nunca no log.
 */
@ExtendWith(OutputCaptureExtension.class)
class RegistroDeAcessosTest extends BancoDeTeste {

  private static final String IP = "203.0.113.77";
  private static final int PORTA = 54321;
  private static final Duration PRAZO = Duration.ofDays(183);
  private static final Duration RENOVACAO_VENCIDA = Duration.ofHours(24).plusSeconds(1);

  @Autowired private PedidoDeEntrada pedidoDeEntrada;
  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private RegistroDeAcessos registro;
  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private RandomGenerator aleatorio;
  @Autowired private PlatformTransactionManager transacoes;

  /** Uma linha de {@code registro_acesso}, com o IP em texto. */
  private record Linha(
      @Nullable UUID conta, String ip, int porta, Instant instante, String evento) {}

  @Test
  void loginRegistraIpPortaEInstante() throws Exception {
    String email = "pessoa-" + UUID.randomUUID() + "@exemplo.com";

    confirmar(pedirLink(email)).andExpect(status().isOk());

    assertThat(linhas()).containsExactly(new Linha(contaDo(email), IP, PORTA, INICIO, "LOGIN"));
  }

  @Test
  void renovacaoRegistraUmaVezPor24h() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    relogio.avancar(RENOVACAO_VENCIDA);

    obterConta(conta).andExpect(status().isOk());
    obterConta(conta).andExpect(status().isOk());

    assertThat(linhasDo("RENOVACAO"))
        .containsExactly(
            new Linha(conta.id().valor(), IP, PORTA, INICIO.plus(RENOVACAO_VENCIDA), "RENOVACAO"));
  }

  @Test
  void chamadaSemRenovacaoNaoRegistra() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    relogio.avancar(Duration.ofHours(24));

    obterConta(conta).andExpect(status().isOk());

    assertThat(linhasDo("RENOVACAO")).isEmpty();
  }

  @Test
  void exclusaoDaContaMantemORegistro() {
    ContaDeTeste conta = contas.criarAtiva();

    exclusao.excluir(conta.id(), true);

    assertThat(linhasDo("LOGIN")).extracting(Linha::conta).containsExactly(conta.id().valor());
  }

  @Test
  void registroFica183DiasExatos() {
    inserirRegistro(INICIO.minus(PRAZO));

    assertThat(acessos().expirar(INICIO.minus(acessos().prazo()))).isZero();
    assertThat(linhas()).hasSize(1);
  }

  @Test
  void registroSai183DiasE1sDepois() {
    inserirRegistro(INICIO.minus(PRAZO).minusSeconds(1));

    assertThat(acessos().expirar(INICIO.minus(acessos().prazo()))).isOne();
    assertThat(linhas()).isEmpty();
  }

  @Test
  void tamanhoContaOsRegistrosVencidos() {
    inserirRegistro(INICIO.minus(PRAZO));
    inserirRegistro(INICIO.minus(PRAZO).minusSeconds(1));

    assertThat(acessos().tamanho()).isOne();
  }

  @Test
  void nomeEPrazo() {
    assertThat(acessos().nome()).isEqualTo("registros-de-acesso");
    assertThat(acessos().prazo()).isEqualTo(Duration.parse("P183D"));
  }

  @Test
  void registrarForaDeTransacaoFalha() {
    Origem origem = new Origem(IP, PORTA);

    assertThatThrownBy(() -> registro.registrar(Optional.empty(), origem, EventoDeAcesso.LOGIN))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(linhas()).isEmpty();
  }

  @Test
  void logDaAplicacaoNaoTrazOIp(CapturedOutput saida) throws Exception {
    confirmar(pedirLink("pessoa-" + UUID.randomUUID() + "@exemplo.com")).andExpect(status().isOk());
    ContaDeTeste conta = contas.criarAtiva();
    relogio.avancar(RENOVACAO_VENCIDA);
    obterConta(conta).andExpect(status().isOk());

    assertThat(linhas()).extracting(Linha::ip).contains(IP);
    assertThat(saida.getAll()).doesNotContain(IP);
  }

  private PoliticaDeRetencao acessos() {
    return politicas.stream()
        .filter(p -> p.nome().equals("registros-de-acesso"))
        .findFirst()
        .orElseThrow();
  }

  /** Grava o link como o pedido grava e devolve o token que iria no e-mail. */
  private String pedirLink(String email) {
    Token token = Token.novo(aleatorio);
    new TransactionTemplate(transacoes)
        .executeWithoutResult(
            status -> pedidoDeEntrada.registrarNaTransacao(Email.de(email), token));
    return token.valor();
  }

  private ResultActions confirmar(String token) throws Exception {
    return mvc.perform(
        post("/api/conta/confirmar")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}")
            .with(OrigemDeTeste.origem(IP, PORTA)));
  }

  private ResultActions obterConta(ContaDeTeste conta) throws Exception {
    String[] cookie = conta.cookie().split("=", 2);
    return mvc.perform(
        get("/api/conta")
            .cookie(new Cookie(cookie[0], cookie[1]))
            .with(OrigemDeTeste.origem(IP, PORTA)));
  }

  private UUID contaDo(String email) {
    return jdbc.sql("select id from conta where email = :email")
        .param("email", email)
        .query(UUID.class)
        .single();
  }

  private void inserirRegistro(Instant instante) {
    jdbc.sql(
            """
            insert into registro_acesso (id, conta_id, ip, porta, instante, evento)
            values (gen_random_uuid(), null, '198.51.100.1', 1234, :instante, 'LOGIN')
            """)
        .param("instante", OffsetDateTime.ofInstant(instante, ZoneOffset.UTC))
        .update();
  }

  private List<Linha> linhas() {
    return jdbc.sql(
            "select conta_id, host(ip) as ip, porta, instante, evento from registro_acesso"
                + " order by instante, evento")
        .query((rs, n) ->
            new Linha(
                rs.getObject("conta_id", UUID.class),
                rs.getString("ip"),
                rs.getInt("porta"),
                rs.getObject("instante", OffsetDateTime.class).toInstant(),
                rs.getString("evento")))
        .list();
  }

  private List<Linha> linhasDo(String evento) {
    return linhas().stream().filter(l -> l.evento().equals(evento)).toList();
  }
}
