package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.DadosDeOrientacao;
import bipotheking.net.duora.perfil.dominio.RepositorioDeOrientacao;
import bipotheking.net.duora.perfil.dominio.RepositorioDePerfis;
import java.time.Clock;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Orientação da própria conta: dado sensível (LGPD, art. 11), gravado só com consentimento
 * explícito e específico, separado do de namoro.
 */
@Service
public class ServicoDeOrientacao {

  private final RepositorioDeOrientacao orientacao;
  private final RepositorioDePerfis perfis;
  private final Clock relogio;

  ServicoDeOrientacao(
      RepositorioDeOrientacao orientacao, RepositorioDePerfis perfis, Clock relogio) {
    this.orientacao = orientacao;
    this.perfis = perfis;
    this.relogio = relogio;
  }

  public Optional<DadosDeOrientacao> minha(ContaId conta) {
    return orientacao.buscar(conta);
  }

  /**
   * Substitui a orientação, com o consentimento registrado agora. Um perfil apagado entre a
   * conferência e a gravação faz a chave estrangeira recusar, com o mesmo código.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio nesta ordem: {@code PERFIL_NECESSARIO},
   *     {@code CONSENTIMENTO_NECESSARIO}, {@code IDENTIDADE_INVALIDA}
   */
  public DadosDeOrientacao salvar(
      ContaId conta,
      @Nullable Orientacao escolhida,
      @Nullable String descricao,
      boolean mostrar,
      boolean consentimento) {
    if (!perfis.existe(conta)) {
      throw CodigoPerfil.PERFIL_NECESSARIO.erro();
    }
    if (!consentimento) {
      throw CodigoPerfil.CONSENTIMENTO_NECESSARIO.erro();
    }
    DadosDeOrientacao dados =
        DadosDeOrientacao.de(escolhida, descricao, mostrar, relogio.instant());
    orientacao.substituir(conta, dados);
    return dados;
  }

  /** Apaga a orientação e o consentimento; idempotente, sem erro mesmo sem perfil. */
  public void apagar(ContaId conta) {
    orientacao.apagar(conta);
  }
}
