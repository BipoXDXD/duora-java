package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.GeradorDeIds;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Cria contas direto no banco, já com uma sessão aberta, para os testes de quem precisa de alguém
 * logado (C4, F e as Etapas 3 e 4). O e-mail é {@code <id>@teste.duora.invalid}; a conta excluída
 * fica sem e-mail, como manda o esquema. Com marcador, o e-mail é {@code
 * <id>+<marcador>@teste.duora.invalid}, que os falsos da verificação de idade leem.
 */
@Component
public class ContasDeTeste {

  private static final LocalDate NASCIMENTO_PADRAO = LocalDate.of(1990, 5, 10);
  private static final String DOMINIO = "@teste.duora.invalid";

  /** Estados de quem já passou pela ativação: eles têm {@code ativada_em}. */
  private static final Set<EstadoConta> JA_ATIVADAS =
      EnumSet.of(EstadoConta.ATIVA, EstadoConta.SUSPENSA, EstadoConta.BANIDA);

  private final JdbcClient jdbc;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;
  private final Sessoes sessoes;

  ContasDeTeste(JdbcClient jdbc, Clock relogio, GeradorDeIds geradorDeIds, Sessoes sessoes) {
    this.jdbc = jdbc;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
    this.sessoes = sessoes;
  }

  /** A conta e o cookie da sessão aberta para ela. */
  public record ContaDeTeste(ContaId id, String cookie) {}

  /**
   * Conta no estado pedido, criada agora, com sessão aberta. Ativa, suspensa e banida saem com
   * {@code ativada_em} = agora; excluída sai sem e-mail e com {@code excluida_em} = agora.
   */
  public ContaDeTeste criar(EstadoConta estado, @Nullable LocalDate nascimento) {
    return inserir(estado, nascimento, "");
  }

  /**
   * Conta aguardando verificação com o marcador na parte local do e-mail ({@code
   * <id>+<marcador>@teste.duora.invalid}): os falsos da prova de vida e do documento respondem por
   * ele. Vários marcadores se juntam com {@code +}, como em {@code idade19+docmenor}.
   */
  public ContaDeTeste criarAguardando(String marcador, LocalDate nascimento) {
    return inserir(EstadoConta.AGUARDANDO_VERIFICACAO, nascimento, "+" + marcador);
  }

  private ContaDeTeste inserir(
      EstadoConta estado, @Nullable LocalDate nascimento, String sufixoDoEmail) {
    UUID id = geradorDeIds.novo();
    OffsetDateTime agora = OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    boolean excluida = estado == EstadoConta.EXCLUIDA;
    jdbc.sql(
            """
            insert into conta
                (id, email, estado, data_nascimento, criada_em, ativada_em, excluida_em)
            values (:id, :email, :estado, :nascimento, :agora, :ativadaEm, :excluidaEm)
            """)
        .param("id", id)
        .param("email", excluida ? null : id + sufixoDoEmail + DOMINIO)
        .param("estado", estado.name())
        .param("nascimento", nascimento)
        .param("agora", agora)
        .param("ativadaEm", JA_ATIVADAS.contains(estado) ? agora : null)
        .param("excluidaEm", excluida ? agora : null)
        .update();
    ContaId conta = new ContaId(id);
    return new ContaDeTeste(conta, "duora_sessao=" + sessoes.abrir(conta, OrigemDeTeste.LOCAL));
  }

  /** Conta ativa, nascida em 1990-05-10, ativada agora. */
  public ContaDeTeste criarAtiva() {
    return criar(EstadoConta.ATIVA, NASCIMENTO_PADRAO);
  }
}
