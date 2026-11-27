package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotência por chave, com reserva antes do efeito. Uso esperado (C5): {@link #reservar} fora de
 * transação. Se vier {@link Reserva.JaRespondida}, devolva a resposta sem efeito. Se vier {@link
 * Reserva.Tua}, guarde o {@code dono}, faça o que precisa de fora (Turnstile) sem transação e então
 * o efeito numa transação curta, com {@link #concluir} como último passo dela. Se algo falhar antes
 * do fim, chame {@link #liberar} com o mesmo dono e relance.
 *
 * <p>A reserva vale por {@code duora.idempotencia.reserva-maxima}: enquanto isso, outra requisição
 * com a mesma chave recebe {@link CodigoWeb#REQUISICAO_EM_ANDAMENTO}; depois, a reserva é tida como
 * abandonada e a próxima requisição a retoma com um dono novo. O dono é um fencing token: quem
 * perdeu a reserva não conclui nem libera a de quem a retomou.
 */
@Component
public class Idempotencia {

  private static final Pattern FORMATO_DA_CHAVE = Pattern.compile("[A-Za-z0-9-]{8,64}");
  private static final Duration ESPERA_EM_ANDAMENTO = Duration.ofSeconds(1);

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;
  private final Duration reservaMaxima;

  Idempotencia(
      JdbcClient jdbc,
      JsonMapper json,
      Clock relogio,
      GeradorDeIds geradorDeIds,
      @Value("${duora.idempotencia.reserva-maxima}") Duration reservaMaxima) {
    this.jdbc = jdbc;
    this.json = json;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
    this.reservaMaxima = reservaMaxima;
  }

  /** O que {@link #reservar} decidiu sobre a chave. */
  public sealed interface Reserva<T> {

    /** A chave é de quem chamou: siga com o efeito e conclua ou libere com este dono. */
    record Tua<T>(UUID dono) implements Reserva<T> {}

    /** A mesma requisição já foi concluída: devolva a resposta sem efeito. */
    record JaRespondida<T>(T resposta) implements Reserva<T> {}
  }

  /**
   * Reserva a chave no escopo, em transação própria.
   *
   * @throws ErroDeNegocio {@link CodigoWeb#CHAVE_DE_IDEMPOTENCIA_INVALIDA} se a chave estiver fora
   *     do formato; {@link CodigoWeb#CHAVE_DE_IDEMPOTENCIA_REUSADA} se a chave veio com outra
   *     impressão; {@link CodigoWeb#REQUISICAO_EM_ANDAMENTO} se outra requisição ainda a segura
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public <T> Reserva<T> reservar(String escopo, String chave, String impressao, Class<T> tipo) {
    if (!FORMATO_DA_CHAVE.matcher(chave).matches()) {
      throw new ErroDeNegocio(
          CodigoWeb.CHAVE_DE_IDEMPOTENCIA_INVALIDA,
          "A chave de idempotência precisa ter de 8 a 64 letras, dígitos ou hifens.");
    }
    OffsetDateTime agora = OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    UUID dono = geradorDeIds.novo();
    while (!inserirReserva(escopo, chave, impressao, dono, agora)) {
      Optional<Linha> existente = buscar(escopo, chave);
      if (existente.isPresent()) {
        return decidir(existente.get(), escopo, chave, impressao, dono, agora, tipo);
      }
      // A reserva foi liberada entre o INSERT e o SELECT: tenta de novo.
    }
    return new Reserva.Tua<>(dono);
  }

  /**
   * Grava a resposta na transação do efeito: se o efeito voltar atrás, a resposta também volta.
   *
   * @throws IllegalStateException se o dono não tem mais a reserva aberta (ela venceu e outra
   *     requisição a retomou): falhar aqui desfaz este efeito, que seria o segundo
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void concluir(String escopo, String chave, UUID dono, Object resposta) {
    Objects.requireNonNull(resposta, "resposta");
    int gravadas =
        jdbc.sql(
                """
                update chave_de_idempotencia set resposta = cast(:resposta as jsonb)
                where escopo = :escopo and chave = :chave and dono = :dono and resposta is null
                """)
            .param("resposta", json.writeValueAsString(resposta))
            .param("escopo", escopo)
            .param("chave", chave)
            .param("dono", dono)
            .update();
    if (gravadas == 0) {
      throw new IllegalStateException("Sem reserva aberta deste dono para a chave de idempotência");
    }
  }

  /**
   * Apaga a reserva sem resposta, para a mesma chave poder tentar de novo depois de uma falha. Não
   * faz nada se o dono já perdeu a reserva ou se ela já tem resposta.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void liberar(String escopo, String chave, UUID dono) {
    jdbc.sql(
            """
            delete from chave_de_idempotencia
            where escopo = :escopo and chave = :chave and dono = :dono and resposta is null
            """)
        .param("escopo", escopo)
        .param("chave", chave)
        .param("dono", dono)
        .update();
  }

  private boolean inserirReserva(
      String escopo, String chave, String impressao, UUID dono, OffsetDateTime agora) {
    return jdbc.sql(
                """
                insert into chave_de_idempotencia
                    (escopo, chave, impressao, dono, resposta, criada_em, reservada_em)
                values (:escopo, :chave, :impressao, :dono, null, :agora, :agora)
                on conflict (escopo, chave) do nothing
                """)
            .param("escopo", escopo)
            .param("chave", chave)
            .param("impressao", impressao)
            .param("dono", dono)
            .param("agora", agora)
            .update()
        == 1;
  }

  private Optional<Linha> buscar(String escopo, String chave) {
    return jdbc.sql(
            """
            select impressao, resposta::text as resposta
            from chave_de_idempotencia where escopo = :escopo and chave = :chave
            """)
        .param("escopo", escopo)
        .param("chave", chave)
        .query((rs, linha) -> new Linha(rs.getString("impressao"), rs.getString("resposta")))
        .optional();
  }

  private <T> Reserva<T> decidir(
      Linha existente,
      String escopo,
      String chave,
      String impressao,
      UUID dono,
      OffsetDateTime agora,
      Class<T> tipo) {
    if (!existente.impressao().equals(impressao)) {
      throw new ErroDeNegocio(
          CodigoWeb.CHAVE_DE_IDEMPOTENCIA_REUSADA,
          "Esta chave de idempotência já foi usada em outra requisição.");
    }
    String resposta = existente.resposta();
    if (resposta != null) {
      return new Reserva.JaRespondida<>(json.readValue(resposta, tipo));
    }
    if (retomarVencida(escopo, chave, impressao, dono, agora)) {
      return new Reserva.Tua<>(dono);
    }
    throw new RequisicaoEmAndamento();
  }

  /**
   * Retoma, com dono novo, a reserva aberta há mais que o máximo; o UPDATE condicional deixa só um
   * retomar.
   */
  private boolean retomarVencida(
      String escopo, String chave, String impressao, UUID dono, OffsetDateTime agora) {
    return jdbc.sql(
                """
                update chave_de_idempotencia set reservada_em = :agora, dono = :dono
                where escopo = :escopo and chave = :chave and impressao = :impressao
                  and resposta is null and reservada_em < :limite
                """)
            .param("agora", agora)
            .param("dono", dono)
            .param("escopo", escopo)
            .param("chave", chave)
            .param("impressao", impressao)
            .param("limite", agora.minus(reservaMaxima))
            .update()
        == 1;
  }

  /** O que a linha guarda e importa para decidir. */
  private record Linha(String impressao, @Nullable String resposta) {}

  /**
   * Outra requisição com a mesma chave ainda segura a reserva: vira 409 com {@code Retry-After}.
   */
  private static final class RequisicaoEmAndamento extends ErroDeNegocio {

    @Serial private static final long serialVersionUID = 1L;

    RequisicaoEmAndamento() {
      super(
          CodigoWeb.REQUISICAO_EM_ANDAMENTO,
          "Esta requisição ainda está em andamento. Tente de novo em instantes.",
          Map.of(),
          ESPERA_EM_ANDAMENTO);
    }
  }
}
