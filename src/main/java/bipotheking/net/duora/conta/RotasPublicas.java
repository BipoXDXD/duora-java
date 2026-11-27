package bipotheking.net.duora.conta;

import java.util.List;

/**
 * Padrões de caminho da API que dispensam sessão; toda outra rota de {@code /api/**} exige uma,
 * inclusive rota inexistente. É a lista do contrato da Etapa 2a; o casamento dos padrões fica com a
 * camada web.
 */
public final class RotasPublicas {

  public static final List<String> PADROES =
      List.of(
          "/api/salas/**",
          "/api/sala/**",
          "/api/conta",
          "/api/conta/entrar",
          "/api/conta/confirmar",
          "/api/conta/configuracao",
          "/api/legal/**",
          "/api/denuncias-publicas");

  private RotasPublicas() {}
}
