package bipotheking.net.duora.retencao;

import java.time.Duration;
import java.time.Instant;

/**
 * O que um módulo apaga e depois de quanto tempo. Um bean por tipo de dado; a {@link
 * ExpiracaoDeDados} roda todas uma vez por dia, cada uma isolada das outras (ADR 0006).
 */
public interface PoliticaDeRetencao {

  /** Nome único em kebab-case ({@code links-de-acesso}), usado no log e nas métricas. */
  String nome();

  /** Quanto tempo o dado vive depois do instante de referência da política. Positivo. */
  Duration prazo();

  /**
   * Apaga tudo cujo instante de referência é anterior a {@code limite} (estrito), em lotes de até
   * 1000 linhas, cada um na própria transação, por {@link Lotes#apagarAteAcabar}; arquivos só
   * depois do commit de cada lote. Idempotente: rodar de novo com o mesmo limite não apaga mais
   * nada. Chamada fora de transação.
   *
   * @return quantos itens apagou
   */
  int expirar(Instant limite);

  /** Quantos itens acumulados existem agora; vira o gauge {@code duora.retencao.tamanho}. */
  long tamanho();
}
