package bipotheking.net.duora.eventos.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.eventos.Evento;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import bipotheking.net.duora.web.BancoDeTeste;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class EventosJdbcTest extends BancoDeTeste {

  @Autowired private RepositorioDeEventos repositorio;
  @Autowired private ContasDeTeste contas;
  @Autowired private TransactionTemplate transacao;

  private ContaId conta;
  private ContaId outra;

  @BeforeEach
  void preparar() {
    conta = contas.criarAtiva().id();
    outra = contas.criarAtiva().id();
  }

  @Test
  void menorSeqDeContaSemEventosEVazio() {
    gravar(outra, 1);

    assertThat(repositorio.menorSeq(conta)).isEmpty();
  }

  @Test
  void menorSeqEOPrimeiroGuardadoDaConta() {
    gravar(outra, 1);
    List<Long> seqs = gravar(conta, 2);

    assertThat(repositorio.menorSeq(conta)).hasValue(seqs.getFirst());
  }

  @Test
  void maiorSeqDeContaSemEventosEVazio() {
    gravar(outra, 1);

    assertThat(repositorio.maiorSeq(conta)).isEmpty();
  }

  @Test
  void maiorSeqEOUltimoGuardadoDaConta() {
    List<Long> seqs = gravar(conta, 2);
    gravar(outra, 1);

    assertThat(repositorio.maiorSeq(conta)).hasValue(seqs.getLast());
  }

  private List<Long> gravar(ContaId para, int quantos) {
    return transacao.execute(status ->
        IntStream.range(0, quantos)
            .mapToObj(
                n -> repositorio.gravar(para, new Evento("fila.estado", Map.of("n", n)), INICIO))
            .toList());
  }
}
