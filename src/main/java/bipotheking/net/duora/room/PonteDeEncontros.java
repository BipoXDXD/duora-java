package bipotheking.net.duora.room;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.game.CatalogoDeCenarios;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.pareamento.ParPareado;
import bipotheking.net.duora.perfil.Perfis;
import bipotheking.net.duora.sala.AberturaDeEncontro;
import bipotheking.net.duora.sala.ConvitePrivado;
import bipotheking.net.duora.sala.EncontroAberto;
import bipotheking.net.duora.sala.OcupacaoDeEncontros;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Hospeda os encontros de {@code sala} nas salas em memória até a Etapa 5. O encontro é a sala
 * pareada, e o id do encontro é o id da sala.
 */
@Component
class PonteDeEncontros implements AberturaDeEncontro, OcupacaoDeEncontros {

  /** Quanto se espera que um encontro dure, para estimar quando a vaga abre. */
  static final Duration DURACAO_PREVISTA = Duration.ofMinutes(30);

  private static final String PRIVADO_PENDENTE = "convite privado: E3";

  private final Salas salas;
  private final Perfis perfis;
  private final CatalogoDeCenarios catalogo;

  PonteDeEncontros(Salas salas, Perfis perfis, CatalogoDeCenarios catalogo) {
    this.salas = salas;
    this.perfis = perfis;
    this.catalogo = catalogo;
  }

  /**
   * @throws IllegalArgumentException se o cenário não existe
   * @throws IllegalStateException se uma das contas não tem perfil público (não está ativa)
   */
  @Override
  public EncontroAberto abrirPareado(ParPareado par, String cenarioId) {
    Cenario cenario =
        catalogo
            .porId(cenarioId)
            .orElseThrow(() -> new IllegalArgumentException("Cenário desconhecido: " + cenarioId));
    String id = salas.criarPareada(par.a(), apelido(par.a()), par.b(), apelido(par.b()), cenario);
    return new EncontroAberto(id, cenario.id(), par.a(), par.b());
  }

  @Override
  public ConvitePrivado abrirPrivado(ContaId criador, String cenarioId) {
    throw new UnsupportedOperationException(PRIVADO_PENDENTE);
  }

  @Override
  public EncontroAberto entrarPrivado(String convite, ContaId convidado) {
    throw new UnsupportedOperationException(PRIVADO_PENDENTE);
  }

  @Override
  public Optional<ContaId> criadorDoConvite(String convite) {
    throw new UnsupportedOperationException(PRIVADO_PENDENTE);
  }

  @Override
  public Optional<String> encontroAtivoDe(ContaId conta) {
    return salas.salaAtivaDe(conta);
  }

  @Override
  public void cancelar(String encontroId) {
    salas.cancelar(encontroId);
  }

  @Override
  public boolean presente(String encontroId, ContaId conta) {
    return salas.presente(encontroId, conta);
  }

  @Override
  public int ativos() {
    return salas.pareadasAtivas().size();
  }

  @Override
  public List<Instant> terminosPrevistos() {
    return salas.pareadasAtivas().stream()
        .map(sala -> sala.abertaEm().plus(DURACAO_PREVISTA))
        .sorted()
        .toList();
  }

  private Apelido apelido(ContaId conta) {
    String apelido =
        perfis
            .publico(conta)
            .orElseThrow(() -> new IllegalStateException("Conta sem perfil público: " + conta))
            .apelido();
    return Apelido.de(apelido);
  }
}
