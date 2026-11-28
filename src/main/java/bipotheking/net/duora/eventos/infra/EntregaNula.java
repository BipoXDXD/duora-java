package bipotheking.net.duora.eventos.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.eventos.EntregaDeEventos;
import bipotheking.net.duora.eventos.Evento;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Entrega sem streams, até o SSE chegar (E2): ninguém está conectado e os eventos ficam só
 * gravados.
 */
class EntregaNula implements EntregaDeEventos {

  /**
   * Registra a entrega nula só se não houver outra. Num {@code @Component} a condição veria o
   * próprio bean e o descartaria; num método {@code @Bean}, não.
   */
  @Configuration(proxyBeanMethods = false)
  static class Registro {

    @Bean
    @ConditionalOnMissingBean(EntregaDeEventos.class)
    EntregaDeEventos entregaNula() {
      return new EntregaNula();
    }
  }

  @Override
  public void acordar(ContaId conta) {
    // Sem streams: nada a acordar.
  }

  @Override
  public void efemero(ContaId conta, Evento evento) {
    // Sem streams: o efêmero se perde, como para quem não está conectado.
  }

  @Override
  public boolean conectada(ContaId conta) {
    return false;
  }

  @Override
  public void encerrar(ContaId conta) {
    // Sem streams: nada a encerrar.
  }
}
