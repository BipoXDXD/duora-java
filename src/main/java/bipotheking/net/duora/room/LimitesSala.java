package bipotheking.net.duora.room;

import java.time.Duration;

/**
 * Limites operacionais das salas.
 *
 * @param maximoSalas salas ativas ao mesmo tempo (lápides não contam)
 * @param maximoTotal teto de memória: ativas mais lápides
 * @param inatividade tempo sem ação válida até a sala expirar; consultas não renovam
 * @param lapide quanto tempo a sala encerrada continua respondendo 410 antes de sumir
 * @param presenca janela em que uma consulta ou ação conta como “conectado”
 */
public record LimitesSala(
    int maximoSalas, int maximoTotal, Duration inatividade, Duration lapide, Duration presenca) {

  public static final LimitesSala PADRAO =
      new LimitesSala(
          20, 40, Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofSeconds(15));
}
