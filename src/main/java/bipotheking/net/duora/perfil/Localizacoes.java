package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.RepositorioDeLocalizacao;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A localização da conta, só com consentimento. A coordenada vira {@link CelulaGeohash} aqui e é
 * descartada: nunca chega ao repositório, ao log nem à resposta.
 */
@Service
public class Localizacoes {

  private final RepositorioDeLocalizacao repositorio;
  private final ServicoDePreferencias preferencias;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  Localizacoes(
      RepositorioDeLocalizacao repositorio,
      ServicoDePreferencias preferencias,
      TransactionTemplate transacao,
      Clock relogio) {
    this.repositorio = repositorio;
    this.preferencias = preferencias;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  /**
   * Consente (ou mantém o consentimento de antes) e grava a célula da coordenada.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code COORDENADA_INVALIDA}
   */
  public void consentirEAtualizar(ContaId conta, double latitude, double longitude) {
    CelulaGeohash celula = CelulaGeohash.de(latitude, longitude);
    repositorio.consentirEGravar(conta, celula, relogio.instant());
  }

  /**
   * Troca a célula de quem já consentiu.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code COORDENADA_INVALIDA}, ou {@code
   *     LOCALIZACAO_SEM_CONSENTIMENTO} se a conta não ativou a localização
   */
  public void atualizar(ContaId conta, double latitude, double longitude) {
    CelulaGeohash celula = CelulaGeohash.de(latitude, longitude);
    if (!repositorio.atualizar(conta, celula, relogio.instant())) {
      throw CodigoLocalizacao.LOCALIZACAO_SEM_CONSENTIMENTO.erro();
    }
  }

  public boolean consentida(ContaId conta) {
    return repositorio.atualizadaEm(conta).isPresent();
  }

  public Optional<CelulaGeohash> celula(ContaId conta) {
    return repositorio.celula(conta);
  }

  public Optional<Instant> atualizadaEm(ContaId conta) {
    return repositorio.atualizadaEm(conta);
  }

  /**
   * Apaga a célula e o consentimento e volta o raio para {@code SEM_LIMITE} (versão +1), na mesma
   * transação; idempotente. O {@code DELETE} vem primeiro: ele espera uma gravação de raio em curso
   * (que trava a linha com {@code for key share}) e, depois, o reajuste pega a linha nova.
   */
  public void revogar(ContaId conta) {
    transacao.executeWithoutResult(_ -> {
      repositorio.apagar(conta);
      preferencias.voltarParaSemLimite(conta);
    });
  }
}
