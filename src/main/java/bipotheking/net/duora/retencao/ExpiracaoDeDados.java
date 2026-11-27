package bipotheking.net.duora.retencao;

import bipotheking.net.duora.nucleo.Correlacao;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Roda todas as {@link PoliticaDeRetencao} em ordem de nome, cada uma com {@code limite = agora -
 * prazo}. A falha de uma vira {@code falhou = true} e log ERROR com o nome dela; as outras seguem.
 * Cada execução tem um correlation ID próprio no MDC. O log leva só nome, contagem e a classe do
 * erro: a mensagem de uma exceção pode trazer dado pessoal.
 *
 * <p>Pública porque o agendamento, os testes de contrato (P11) e a exclusão de conta (C12) a
 * chamam.
 */
@Component
public class ExpiracaoDeDados {

  private static final Logger LOG = LoggerFactory.getLogger(ExpiracaoDeDados.class);

  private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
  private static final String TAG = "politica";

  private final List<Registrada> politicas;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;

  /**
   * @throws IllegalStateException se dois nomes se repetem, se um nome não é kebab-case ou se um
   *     prazo não é positivo (zero ou negativo apagaria dado vivo)
   */
  public ExpiracaoDeDados(
      List<PoliticaDeRetencao> politicas,
      Clock relogio,
      GeradorDeIds geradorDeIds,
      MeterRegistry metricas) {
    validar(politicas);
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
    this.politicas =
        politicas.stream()
            .sorted(Comparator.comparing(PoliticaDeRetencao::nome))
            .map(politica -> registrar(politica, metricas))
            .toList();
  }

  /**
   * Roda as políticas uma vez. Execuções simultâneas são serializadas.
   *
   * @return um resultado por política, em ordem de nome
   * @throws IllegalStateException se chamada dentro de uma transação: cada lote de cada política
   *     precisa da própria transação, e uma transação de fora juntaria todos numa só, longa
   */
  public synchronized List<ResultadoDaExpiracao> executar() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("A expiração de dados não pode rodar dentro de transação");
    }
    String anterior = MDC.get(Correlacao.CHAVE_MDC);
    MDC.put(Correlacao.CHAVE_MDC, geradorDeIds.novo().toString());
    try {
      Instant agora = relogio.instant();
      List<ResultadoDaExpiracao> resultados = new ArrayList<>(politicas.size());
      for (Registrada registrada : politicas) {
        resultados.add(expirar(registrada, agora));
      }
      return List.copyOf(resultados);
    } finally {
      if (anterior == null) {
        MDC.remove(Correlacao.CHAVE_MDC);
      } else {
        MDC.put(Correlacao.CHAVE_MDC, anterior);
      }
    }
  }

  /** O prazo de cada política, por nome, em ordem de nome. */
  public Map<String, Duration> prazos() {
    Map<String, Duration> prazos = new LinkedHashMap<>();
    politicas.forEach(r -> prazos.put(r.politica().nome(), r.politica().prazo()));
    return Collections.unmodifiableMap(prazos);
  }

  private static ResultadoDaExpiracao expirar(Registrada registrada, Instant agora) {
    PoliticaDeRetencao politica = registrada.politica();
    String nome = politica.nome();
    try {
      int removidos = politica.expirar(agora.minus(politica.prazo()));
      registrada.removidos().increment(removidos);
      LOG.info("Política de retenção {} removeu {} itens", nome, removidos);
      return new ResultadoDaExpiracao(nome, removidos, false);
    } catch (RuntimeException erro) {
      registrada.falhas().increment();
      LOG.error("Política de retenção {} falhou ({})", nome, erro.getClass().getName());
      return new ResultadoDaExpiracao(nome, 0, true);
    }
  }

  private static void validar(List<PoliticaDeRetencao> politicas) {
    Set<String> nomes = new HashSet<>();
    for (PoliticaDeRetencao politica : politicas) {
      String nome = politica.nome();
      if (!nomes.add(nome)) {
        throw new IllegalStateException("Política de retenção repetida: " + nome);
      }
      if (!KEBAB_CASE.matcher(nome).matches()) {
        throw new IllegalStateException("Nome de política fora de kebab-case: " + nome);
      }
      Duration prazo = politica.prazo();
      if (prazo.isZero() || prazo.isNegative()) {
        throw new IllegalStateException("Prazo da política " + nome + " não é positivo: " + prazo);
      }
    }
  }

  private static Registrada registrar(PoliticaDeRetencao politica, MeterRegistry metricas) {
    String nome = politica.nome();
    Gauge.builder("duora.retencao.tamanho", politica, PoliticaDeRetencao::tamanho)
        .description("Itens acumulados à espera de expirar")
        .tag(TAG, nome)
        .strongReference(true)
        .register(metricas);
    Counter removidos =
        Counter.builder("duora.retencao.removidos")
            .description("Itens apagados pela política de retenção")
            .tag(TAG, nome)
            .register(metricas);
    Counter falhas =
        Counter.builder("duora.retencao.falhas")
            .description("Execuções da política de retenção que falharam")
            .tag(TAG, nome)
            .register(metricas);
    return new Registrada(politica, removidos, falhas);
  }

  /** A política com os contadores dela. */
  private record Registrada(PoliticaDeRetencao politica, Counter removidos, Counter falhas) {}
}
