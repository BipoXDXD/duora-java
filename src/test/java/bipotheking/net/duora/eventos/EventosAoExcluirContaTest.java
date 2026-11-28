package bipotheking.net.duora.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.eventos.dominio.EventoGravado;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import bipotheking.net.duora.web.BancoDeTeste;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A conta excluída vira lápide (a linha não sai), então o {@code on delete cascade} de {@code
 * evento_conta} nunca dispara: os eventos saem pelo gancho da exclusão.
 */
@Import(EntregaEspia.class)
class EventosAoExcluirContaTest extends BancoDeTeste {

  @Autowired private CanalDeEventos canal;
  @Autowired private RepositorioDeEventos repositorio;
  @Autowired private EntregaEspia espia;
  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private EventosAoExcluirConta gancho;
  @Autowired private TransactionTemplate transacao;

  @BeforeEach
  void preparar() {
    espia.esquecer();
  }

  @Test
  void excluirApagaOsEventosDaConta() {
    ContaId excluida = contas.criarAtiva().id();
    ContaId outra = contas.criarAtiva().id();
    transacao.executeWithoutResult(status ->
        canal.publicar(List.of(new Envio(excluida, evento()), new Envio(outra, evento()))));

    exclusao.excluir(excluida, true);

    assertThat(repositorio.depoisDe(excluida, 0, 10)).isEmpty();
    assertThat(repositorio.depoisDe(outra, 0, 10))
        .extracting(EventoGravado::nome)
        .containsExactly("fila.estado");
  }

  @Test
  void excluirEncerraStreamsSoDepoisDoCommit() {
    ContaId conta = contas.criarAtiva().id();

    List<ContaId> antesDoCommit =
        transacao.execute(status -> {
          gancho.excluir(conta);
          return espia.encerradas();
        });

    assertThat(antesDoCommit).isEmpty();
    assertThat(espia.encerradas()).containsExactly(conta);
  }

  private static Evento evento() {
    return new Evento("fila.estado", Map.of());
  }
}
