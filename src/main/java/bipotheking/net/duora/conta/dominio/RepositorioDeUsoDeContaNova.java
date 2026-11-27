package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.TipoDeLimiteNovo;
import java.time.LocalDate;
import java.util.OptionalInt;

/** O quanto cada conta nova consumiu por dia e tipo. */
public interface RepositorioDeUsoDeContaNova {

  /**
   * Soma um ao uso do dia se ele ainda estiver abaixo de {@code limite}, num só comando atômico:
   * consumos simultâneos nunca passam do limite. Roda na transação do chamador, que segura a linha
   * até o commit.
   *
   * @param limite pelo menos 1
   * @return o uso depois de somar; vazio se o dia já estava no limite
   * @throws IllegalArgumentException se {@code limite} é menor que 1
   */
  OptionalInt consumir(ContaId conta, LocalDate dia, TipoDeLimiteNovo tipo, int limite);
}
