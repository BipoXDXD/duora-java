package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.ArmazemDeImagens;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.Variante;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Arquivos de foto sem linha na tabela (sobra de uma falha) saem um dia depois do mtime; os que têm
 * linha ficam. O mtime é ajustado à mão em relação ao relógio de teste.
 */
class RetencaoDeFotosOrfasTest extends BancoDeTeste {

  private static final String CATEGORIA = "perfil";
  private static final Duration VENCIDO = Duration.ofHours(25);
  private static final Duration RECENTE = Duration.ofHours(23);

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private FotosDeTeste fotosDeTeste;
  @Autowired private ContasDeTeste contas;
  @Autowired private ArmazemDeImagens armazem;

  private final List<FotoId> criadas = new ArrayList<>();

  /** O armazém é dividido entre os testes: o que este teste criou não fica para o próximo. */
  @AfterEach
  void apagarCriadas() {
    criadas.forEach(foto -> armazem.apagar(CATEGORIA, foto.valor()));
  }

  @Test
  void arquivoSemLinhaVencidoSome() throws IOException {
    FotoId orfa = orfa(VENCIDO);

    retencao().expirar(relogio.instant().minus(retencao().prazo()));

    assertThat(armazem.ler(CATEGORIA, orfa.valor(), Variante.NITIDA)).isEmpty();
    assertThat(armazem.ler(CATEGORIA, orfa.valor(), Variante.BORRADA)).isEmpty();
  }

  @Test
  void arquivoComLinhaFica() throws IOException {
    FotoId comLinha = comLinha(VENCIDO);

    retencao().expirar(relogio.instant().minus(retencao().prazo()));

    assertThat(armazem.ler(CATEGORIA, comLinha.valor(), Variante.NITIDA)).isPresent();
    assertThat(armazem.ler(CATEGORIA, comLinha.valor(), Variante.BORRADA)).isPresent();
  }

  @Test
  void orfaoRecenteFica() throws IOException {
    FotoId recente = orfa(RECENTE);

    retencao().expirar(relogio.instant().minus(retencao().prazo()));

    assertThat(armazem.ler(CATEGORIA, recente.valor(), Variante.NITIDA)).isPresent();
  }

  /** Comparado com o antes: sobras vencidas de outras rodadas podem estar no armazém. */
  @Test
  void tamanhoContaOrfaosVencidos() throws IOException {
    long antes = retencao().tamanho();
    orfa(VENCIDO);
    orfa(VENCIDO);
    orfa(RECENTE);
    comLinha(VENCIDO);

    assertThat(retencao().tamanho()).isEqualTo(antes + 2);
  }

  private PoliticaDeRetencao retencao() {
    return politicas.stream()
        .filter(politica -> politica.nome().equals("fotos-orfas"))
        .findFirst()
        .orElseThrow();
  }

  /** Uma foto enviada cuja linha some: os arquivos ficam sem dono. */
  private FotoId orfa(Duration idade) throws IOException {
    FotoId foto = comLinha(idade);
    jdbc.sql("delete from foto where id = :id").param("id", foto.valor()).update();
    return foto;
  }

  private FotoId comLinha(Duration idade) throws IOException {
    FotoId foto = fotosDeTeste.enviar(contas.criarAtiva().id());
    criadas.add(foto);
    FileTime mtime = FileTime.from(relogio.instant().minus(idade));
    for (Variante variante : Variante.values()) {
      Files.setLastModifiedTime(
          armazem.ler(CATEGORIA, foto.valor(), variante).orElseThrow().getFile().toPath(), mtime);
    }
    return foto;
  }
}
