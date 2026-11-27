package bipotheking.net.duora.conta;

/**
 * Gancho de outro módulo na exclusão da conta: apaga ou anonimiza o que o módulo guarda dela.
 *
 * <p>Roda na transação da exclusão, antes dos dados da própria conta, na ordem do {@code @Order}:
 * uma falha desfaz a exclusão inteira. Arquivos fora do banco só saem no {@code afterCommit}, para
 * um rollback não deixar a linha sem o arquivo.
 */
public interface AoExcluirConta {

  void excluir(ContaId conta);
}
