package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.DadosDeNamoro;
import bipotheking.net.duora.perfil.dominio.Perfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeNamoro;
import bipotheking.net.duora.perfil.dominio.RepositorioDePerfis;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gênero e interesses da própria conta: dados sensíveis (LGPD, art. 11), gravados só com
 * consentimento explícito e só para quem marcou Namoro.
 */
@Service
public class ServicoDeNamoro {

  private final RepositorioDeNamoro namoro;
  private final RepositorioDePerfis perfis;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  ServicoDeNamoro(
      RepositorioDeNamoro namoro,
      RepositorioDePerfis perfis,
      TransactionTemplate transacao,
      Clock relogio) {
    this.namoro = namoro;
    this.perfis = perfis;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  public Optional<DadosDeNamoro> meus(ContaId conta) {
    return namoro.buscar(conta);
  }

  public Optional<Instant> consentidoEm(ContaId conta) {
    return namoro.consentidoEm(conta);
  }

  /**
   * Confere, na ordem do contrato, o que vem antes dos dados: o perfil, a intenção de namoro e o
   * consentimento. O controller chama antes de validar os dados, para a recusa sair na ordem certa.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code PERFIL_NECESSARIO}, {@code
   *     INTENCAO_SEM_NAMORO} ou {@code CONSENTIMENTO_NECESSARIO}
   */
  public void exigirCondicoes(ContaId conta, boolean consentimento) {
    Perfil perfil = perfis.buscar(conta).orElseThrow(CodigoPerfil.PERFIL_NECESSARIO::erro);
    if (!perfil.dados().intencoes().querNamoro()) {
      throw CodigoPerfil.INTENCAO_SEM_NAMORO.erro();
    }
    if (!consentimento) {
      throw CodigoPerfil.CONSENTIMENTO_NECESSARIO.erro();
    }
  }

  /**
   * Substitui todos os dados, com o consentimento registrado agora.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio os de {@link #exigirCondicoes}; {@code
   *     INTENCAO_SEM_NAMORO} também se a intenção sair no meio do caminho (a chave estrangeira
   *     recusa)
   */
  public DadosDeNamoro salvar(ContaId conta, DadosDeNamoro dados, boolean consentimento) {
    Instant agora = relogio.instant();
    transacao.executeWithoutResult(_ -> {
      exigirCondicoes(conta, consentimento);
      namoro.substituir(conta, dados, agora);
    });
    return dados;
  }

  /**
   * Apaga os dados e troca as intenções por só amizade, subindo a versão do perfil; idempotente,
   * sem erro mesmo sem perfil. Uma gravação simultânea que entre entre o apagar e a troca faz a
   * chave estrangeira recusar a troca; a segunda tentativa apaga essa gravação também.
   */
  public void revogar(ContaId conta) {
    try {
      apagarETrocar(conta);
    } catch (DataIntegrityViolationException corrida) {
      apagarETrocar(conta);
    }
  }

  private void apagarETrocar(ContaId conta) {
    Instant agora = relogio.instant();
    transacao.executeWithoutResult(_ -> {
      namoro.apagar(conta);
      perfis.trocarIntencoesPorAmizade(conta, agora);
    });
  }
}
