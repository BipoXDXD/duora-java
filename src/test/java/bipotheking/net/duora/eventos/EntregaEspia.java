package bipotheking.net.duora.eventos;

import bipotheking.net.duora.conta.ContaId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;

/**
 * {@link EntregaDeEventos} de teste: anota quem foi acordado, recebeu efêmero ou teve os streams
 * encerrados, e diz conectada só a conta que o teste {@link #conectar conectou}. Entra por
 * {@code @Import(EntregaEspia.class)} e vence a entrega da aplicação ({@code @Primary}). O contexto
 * é dividido entre os testes: quem usa chama {@link #esquecer} antes de cada um.
 */
@TestConfiguration(proxyBeanMethods = false)
@Primary
public class EntregaEspia implements EntregaDeEventos {

  private final Set<ContaId> conectadas = ConcurrentHashMap.newKeySet();
  private final List<ContaId> acordadas = new CopyOnWriteArrayList<>();
  private final List<Envio> efemeros = new CopyOnWriteArrayList<>();
  private final List<ContaId> encerradas = new CopyOnWriteArrayList<>();

  public void conectar(ContaId conta) {
    conectadas.add(conta);
  }

  public void desconectar(ContaId conta) {
    conectadas.remove(conta);
  }

  /** As contas acordadas, na ordem e com repetição. */
  public List<ContaId> acordadas() {
    return List.copyOf(acordadas);
  }

  /** Os efêmeros entregues, na ordem. */
  public List<Envio> efemeros() {
    return List.copyOf(efemeros);
  }

  /** As contas que tiveram os streams encerrados, na ordem e com repetição. */
  public List<ContaId> encerradas() {
    return List.copyOf(encerradas);
  }

  /** Volta ao estado inicial: ninguém conectado, nada anotado. */
  public void esquecer() {
    conectadas.clear();
    acordadas.clear();
    efemeros.clear();
    encerradas.clear();
  }

  @Override
  public void acordar(ContaId conta) {
    acordadas.add(conta);
  }

  @Override
  public void efemero(ContaId conta, Evento evento) {
    efemeros.add(new Envio(conta, evento));
  }

  @Override
  public boolean conectada(ContaId conta) {
    return conectadas.contains(conta);
  }

  @Override
  public void encerrar(ContaId conta) {
    encerradas.add(conta);
  }
}
