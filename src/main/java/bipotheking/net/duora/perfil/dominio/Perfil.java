package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.util.Objects;

/**
 * O perfil gravado de uma conta. A {@code versao} começa em 1 e sobe a cada edição: é o controle
 * otimista contra duas abas salvando por cima uma da outra.
 */
public record Perfil(ContaId conta, EdicaoDoPerfil dados, long versao) {

  public Perfil {
    Objects.requireNonNull(conta, "conta");
    Objects.requireNonNull(dados, "dados");
    if (versao < 1) {
      throw new IllegalArgumentException("Versão do perfil menor que 1: " + versao);
    }
  }
}
