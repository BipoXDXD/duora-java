package bipotheking.net.duora.eventos;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Um evento de tempo real para uma conta: o nome vira o {@code event:} do SSE e os dados, o JSON do
 * {@code data:}. O nome começa por um dos domínios que publicam; {@code stream.*} é transporte,
 * escrito só pelo stream, e fica de fora.
 *
 * @param nome {@code <domínio>.<nome>}, como {@code fila.pareado}
 * @param dados o que vai serializado em JSON
 */
public record Evento(String nome, Object dados) {

  private static final Pattern NOME =
      Pattern.compile("^(fila|encontro|chat|voz|conexao|moderacao)\\.[a-z][a-zA-Z.]*$");

  public Evento {
    Objects.requireNonNull(nome, "nome");
    Objects.requireNonNull(dados, "dados");
    if (!NOME.matcher(nome).matches()) {
      throw new IllegalArgumentException("Nome de evento fora do padrão: " + nome);
    }
  }
}
