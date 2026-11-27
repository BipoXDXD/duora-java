package bipotheking.net.duora.conta.dominio;

import java.util.Objects;

/**
 * Sessão de prova de vida aberta no fornecedor.
 *
 * @param id identificador da sessão no fornecedor
 * @param regiao região onde a sessão vive; sempre {@code us-east-1}
 */
public record SessaoDeProva(String id, String regiao) {

  public SessaoDeProva {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(regiao, "regiao");
  }
}
