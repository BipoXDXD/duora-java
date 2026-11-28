package bipotheking.net.duora.sala;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.ParPareado;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * As portas dos encontros para os testes da fila, sem sala de verdade: a ocupação, os términos
 * previstos e quem está em encontro são o que o teste configurar. Entra por
 * {@code @Import(EncontrosFalsos.class)}; o contexto é dividido entre os testes, então quem usa
 * chama {@link #esquecer} antes de cada um. Abrir, entrar e cancelar não fazem parte da fila e
 * lançam {@link UnsupportedOperationException}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EncontrosFalsos implements AberturaDeEncontro, OcupacaoDeEncontros {

  private final AtomicInteger ativos = new AtomicInteger();
  private final AtomicReference<List<Instant>> terminos = new AtomicReference<>(List.of());
  private final Map<ContaId, String> emEncontro = new ConcurrentHashMap<>();

  /** Quantos encontros ocupam vaga e quando terminam (em ordem crescente). */
  public void ocupar(int quantos, List<Instant> terminosPrevistos) {
    ativos.set(quantos);
    terminos.set(List.copyOf(terminosPrevistos));
  }

  /** Põe a conta num encontro em andamento. */
  public void emEncontro(ContaId conta, String encontroId) {
    emEncontro.put(conta, encontroId);
  }

  /** Volta ao estado inicial: nada ocupado, ninguém em encontro. */
  public void esquecer() {
    ativos.set(0);
    terminos.set(List.of());
    emEncontro.clear();
  }

  @Override
  public int ativos() {
    return ativos.get();
  }

  @Override
  public List<Instant> terminosPrevistos() {
    return terminos.get();
  }

  @Override
  public Optional<String> encontroAtivoDe(ContaId conta) {
    return Optional.ofNullable(emEncontro.get(conta));
  }

  @Override
  public EncontroAberto abrirPareado(ParPareado par, String cenarioId) {
    throw new UnsupportedOperationException("fora da fila");
  }

  @Override
  public ConvitePrivado abrirPrivado(ContaId criador, String cenarioId) {
    throw new UnsupportedOperationException("fora da fila");
  }

  @Override
  public EncontroAberto entrarPrivado(String convite, ContaId convidado) {
    throw new UnsupportedOperationException("fora da fila");
  }

  @Override
  public Optional<ContaId> criadorDoConvite(String convite) {
    throw new UnsupportedOperationException("fora da fila");
  }

  @Override
  public void cancelar(String encontroId) {
    throw new UnsupportedOperationException("fora da fila");
  }

  @Override
  public boolean presente(String encontroId, ContaId conta) {
    throw new UnsupportedOperationException("fora da fila");
  }
}
