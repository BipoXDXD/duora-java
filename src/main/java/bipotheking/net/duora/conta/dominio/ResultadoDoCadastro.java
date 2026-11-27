package bipotheking.net.duora.conta.dominio;

/**
 * Desfecho do cadastro de uma conta pendente com data válida. Não carrega a data de nascimento, que
 * nunca vai para o log.
 */
public sealed interface ResultadoDoCadastro
    permits ResultadoDoCadastro.Concluido, ResultadoDoCadastro.RecusadoPorIdade {

  /** Maior de idade: a conta segue para a verificação. */
  record Concluido() implements ResultadoDoCadastro {}

  /** Menor de idade: a conta é excluída e o e-mail, bloqueado. */
  record RecusadoPorIdade() implements ResultadoDoCadastro {}
}
