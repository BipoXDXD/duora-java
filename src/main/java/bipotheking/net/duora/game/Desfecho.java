package bipotheking.net.duora.game;

/**
 * Como um cenário traduz a composição revelada em {@link Resultado} (Strategy por cenário).
 *
 * <p>Invariante: puro e determinístico (sem relógio, sorteio nem estado; a mesma composição dá
 * sempre o mesmo resultado, com as chaves na mesma ordem) e chamado só com composição completa
 * ({@link Cenario#completa(Composicao)}).
 */
@FunctionalInterface
public interface Desfecho {

  Resultado de(Composicao composicao, Cenario cenario);
}
