package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.AjustesDeBusca;
import bipotheking.net.duora.perfil.dominio.AjustesVersionados;
import bipotheking.net.duora.perfil.dominio.RepositorioDeAjustes;
import bipotheking.net.duora.perfil.dominio.RepositorioDePerfis;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Os ajustes de busca da própria conta, com controle otimista pela versão. */
@Service
public class ServicoDePreferencias {

  private static final AjustesVersionados SEM_AJUSTES =
      new AjustesVersionados(AjustesDeBusca.PADRAO, null);

  private final RepositorioDeAjustes ajustes;
  private final RepositorioDePerfis perfis;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  ServicoDePreferencias(
      RepositorioDeAjustes ajustes,
      RepositorioDePerfis perfis,
      TransactionTemplate transacao,
      Clock relogio) {
    this.ajustes = ajustes;
    this.perfis = perfis;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  /** Os ajustes gravados, ou os padrões (sem versão) se a conta nunca salvou. */
  public AjustesVersionados ajustes(ContaId conta) {
    return ajustes.buscar(conta).orElse(SEM_AJUSTES);
  }

  /**
   * Sem versão, cria os ajustes na versão 1; com a versão que a pessoa leu, troca e sobe a versão.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code PERFIL_NECESSARIO} sem perfil; {@code
   *     VERSAO_OBSOLETA} se a versão não bate com a gravada; {@code LOCALIZACAO_NECESSARIA} se o
   *     raio pede a localização e ela não está ativa
   */
  public AjustesVersionados salvar(
      ContaId conta, @Nullable Long versaoEsperada, AjustesDeBusca novos) {
    Instant agora = relogio.instant();
    return Objects.requireNonNull(
        transacao.execute(_ -> gravar(conta, versaoEsperada, novos, agora)));
  }

  /** Volta o raio para {@code SEM_LIMITE} e sobe a versão; nada muda se ele já estava lá. */
  public void voltarParaSemLimite(ContaId conta) {
    Instant agora = relogio.instant();
    transacao.executeWithoutResult(_ -> ajustes.voltarParaSemLimite(conta, agora));
  }

  private AjustesVersionados gravar(
      ContaId conta, @Nullable Long versaoEsperada, AjustesDeBusca novos, Instant agora) {
    if (!perfis.existe(conta)) {
      throw CodigoPerfil.PERFIL_NECESSARIO.erro();
    }
    if (versaoEsperada == null) {
      if (ajustes.criar(conta, novos, agora)) {
        return new AjustesVersionados(novos, 1L);
      }
      throw recusa(conta, null);
    }
    return ajustes
        .atualizar(conta, versaoEsperada, novos, agora)
        .orElseThrow(() -> recusa(conta, versaoEsperada));
  }

  /**
   * Nenhuma linha mudou: ou a versão não bate (outra edição, ou uma revogação da localização,
   * chegou antes), ou o raio pede a localização e ela não está ativa.
   */
  private RuntimeException recusa(ContaId conta, @Nullable Long versaoEsperada) {
    Optional<Long> gravada = ajustes.buscar(conta).map(AjustesVersionados::versao);
    if (!Objects.equals(gravada.orElse(null), versaoEsperada)) {
      return CodigoPerfil.VERSAO_OBSOLETA.erro();
    }
    return CodigoPerfil.LOCALIZACAO_NECESSARIA.erro();
  }
}
