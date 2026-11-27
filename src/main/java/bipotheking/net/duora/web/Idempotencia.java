package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotência por chave, com reserva antes do efeito. Uso esperado: {@link #reservar} fora de
 * transação; se vier vazio, a chave é de quem chamou, que faz o efeito numa transação curta e chama
 * {@link #concluir} dentro dela; se o efeito falhar, {@link #liberar} e relança.
 *
 * <p>A reserva vale por {@code duora.idempotencia.reserva-maxima}: enquanto isso, outra requisição
 * com a mesma chave recebe {@link CodigoWeb#REQUISICAO_EM_ANDAMENTO}; depois, a reserva é tida como
 * abandonada e a próxima requisição a retoma.
 */
@Component
public class Idempotencia {

  private static final Pattern FORMATO_DA_CHAVE = Pattern.compile("[A-Za-z0-9-]{8,64}");
  private static final Duration ESPERA_EM_ANDAMENTO = Duration.ofSeconds(1);

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Clock relogio;
  private final Duration reservaMaxima;

  Idempotencia(
      JdbcClient jdbc,
      JsonMapper json,
      Clock relogio,
      @Value("${duora.idempotencia.reserva-maxima}") Duration reservaMaxima) {
    this.jdbc = jdbc;
    this.json = json;
    this.relogio = relogio;
    this.reservaMaxima = reservaMaxima;
  }

  /**
   * Reserva a chave no escopo, em transação própria.
   *
   * @return vazio se a chave é de quem chamou (siga com o efeito); a resposta gravada, se a mesma
   *     requisição já foi concluída (devolva-a sem efeito)
   * @throws ErroDeNegocio {@link CodigoWeb#CHAVE_DE_IDEMPOTENCIA_INVALIDA} se a chave estiver fora
   *     do formato; {@link CodigoWeb#CHAVE_DE_IDEMPOTENCIA_REUSADA} se a chave veio com outra
   *     impressão; {@link CodigoWeb#REQUISICAO_EM_ANDAMENTO} se outra requisição ainda a segura
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public <T> Optional<T> reservar(String escopo, String chave, String impressao, Class<T> tipo) {
    if (!FORMATO_DA_CHAVE.matcher(chave).matches()) {
      throw new ErroDeNegocio(
          CodigoWeb.CHAVE_DE_IDEMPOTENCIA_INVALIDA,
          "A chave de idempotência precisa ter de 8 a 64 letras, dígitos ou hifens.");
    }
    OffsetDateTime agora = OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    while (!inserirReserva(escopo, chave, impressao, agora)) {
      Optional<Reserva> existente = buscar(escopo, chave);
      if (existente.isPresent()) {
        return decidir(existente.get(), escopo, chave, impressao, agora, tipo);
      }
      // A reserva foi liberada entre o INSERT e o SELECT: tenta de novo.
    }
    return Optional.empty();
  }

  /** Grava a resposta na transação do efeito: se o efeito voltar atrás, a resposta também volta. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void concluir(String escopo, String chave, Object resposta) {
    int gravadas =
        jdbc.sql(
                """
                update chave_de_idempotencia set resposta = cast(:resposta as jsonb)
                where escopo = :escopo and chave = :chave and resposta is null
                """)
            .param("resposta", json.writeValueAsString(resposta))
            .param("escopo", escopo)
            .param("chave", chave)
            .update();
    if (gravadas == 0) {
      // Sem reserva aberta: ela venceu e outra requisição a retomou e concluiu. Falhar aqui desfaz
      // este efeito, que seria o segundo.
      throw new IllegalStateException("Sem reserva aberta para a chave de idempotência");
    }
  }

  /** Apaga a reserva sem resposta, para a mesma chave poder tentar de novo depois de uma falha. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void liberar(String escopo, String chave) {
    jdbc.sql(
            """
            delete from chave_de_idempotencia
            where escopo = :escopo and chave = :chave and resposta is null
            """)
        .param("escopo", escopo)
        .param("chave", chave)
        .update();
  }

  private boolean inserirReserva(
      String escopo, String chave, String impressao, OffsetDateTime agora) {
    return jdbc.sql(
                """
                insert into chave_de_idempotencia
                    (escopo, chave, impressao, resposta, criada_em, reservada_em)
                values (:escopo, :chave, :impressao, null, :agora, :agora)
                on conflict do nothing
                """)
            .param("escopo", escopo)
            .param("chave", chave)
            .param("impressao", impressao)
            .param("agora", agora)
            .update()
        == 1;
  }

  private Optional<Reserva> buscar(String escopo, String chave) {
    return jdbc.sql(
            """
            select impressao, resposta::text as resposta
            from chave_de_idempotencia where escopo = :escopo and chave = :chave
            """)
        .param("escopo", escopo)
        .param("chave", chave)
        .query((rs, linha) -> new Reserva(rs.getString("impressao"), rs.getString("resposta")))
        .optional();
  }

  private <T> Optional<T> decidir(
      Reserva reserva,
      String escopo,
      String chave,
      String impressao,
      OffsetDateTime agora,
      Class<T> tipo) {
    if (!reserva.impressao().equals(impressao)) {
      throw new ErroDeNegocio(
          CodigoWeb.CHAVE_DE_IDEMPOTENCIA_REUSADA,
          "Esta chave de idempotência já foi usada em outra requisição.");
    }
    String resposta = reserva.resposta();
    if (resposta != null) {
      return Optional.of(json.readValue(resposta, tipo));
    }
    if (retomarVencida(escopo, chave, agora)) {
      return Optional.empty();
    }
    throw new RequisicaoEmAndamento();
  }

  /** Retoma a reserva aberta há mais que o máximo; o UPDATE condicional deixa só um retomar. */
  private boolean retomarVencida(String escopo, String chave, OffsetDateTime agora) {
    return jdbc.sql(
                """
                update chave_de_idempotencia set reservada_em = :agora
                where escopo = :escopo and chave = :chave
                  and resposta is null and reservada_em < :limite
                """)
            .param("agora", agora)
            .param("limite", agora.minus(reservaMaxima))
            .param("escopo", escopo)
            .param("chave", chave)
            .update()
        == 1;
  }

  private record Reserva(String impressao, @Nullable String resposta) {}

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
