package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import bipotheking.net.duora.retencao.Lotes;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.ArmazemDeImagens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Arquivos de foto sem linha na tabela {@code foto} saem 1 dia depois do mtime. O normal é a troca,
 * a recusa, a remoção e a exclusão apagarem os arquivos logo depois do commit; esta política só
 * varre o que uma falha deixou (o disco que recusou o {@code apagar}, o processo que caiu entre o
 * commit e a limpeza). O dia de folga protege o envio em curso, cujos arquivos chegam antes de a
 * linha ficar visível.
 */
@Component
class RetencaoDeFotosOrfas implements PoliticaDeRetencao {

  private static final String CATEGORIA = "perfil";
  private static final Duration PRAZO = Duration.parse("P1D");
  private static final int LOTE = 1000;

  private final ArmazemDeImagens armazem;
  private final RepositorioDeFotos fotos;
  private final Clock relogio;

  RetencaoDeFotosOrfas(ArmazemDeImagens armazem, RepositorioDeFotos fotos, Clock relogio) {
    this.armazem = armazem;
    this.fotos = fotos;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "fotos-orfas";
  }

  @Override
  public Duration prazo() {
    return PRAZO;
  }

  /**
   * Varre o armazém em páginas de 1000 ids; cada lote do {@link Lotes} avança as páginas até apagar
   * algum órfão ou chegar ao fim, para uma página só de fotos com linha não encerrar a varredura.
   * Um id órfão nunca ganha linha depois: todo envio usa um id novo.
   */
  @Override
  public int expirar(Instant limite) {
    Varredura varredura = new Varredura(limite);
    return Lotes.apagarAteAcabar(varredura::proximoLote);
  }

  @Override
  public long tamanho() {
    Instant limite = relogio.instant().minus(PRAZO);
    long orfas = 0;
    @Nullable UUID cursor = null;
    List<UUID> pagina;
    while (!(pagina = armazem.guardadasAntesDe(CATEGORIA, limite, cursor, LOTE)).isEmpty()) {
      orfas += orfasDa(pagina).size();
      cursor = pagina.getLast();
    }
    return orfas;
  }

  private List<UUID> orfasDa(List<UUID> pagina) {
    Set<UUID> comLinha = fotos.existentes(pagina);
    return pagina.stream().filter(id -> !comLinha.contains(id)).toList();
  }

  /** O cursor de uma rodada do {@link #expirar}. */
  private final class Varredura {

    private final Instant limite;
    private @Nullable UUID cursor;

    Varredura(Instant limite) {
      this.limite = limite;
    }

    /** Apaga os órfãos da próxima página que tiver algum; zero quando as páginas acabam. */
    int proximoLote() {
      List<UUID> pagina;
      while (!(pagina = armazem.guardadasAntesDe(CATEGORIA, limite, cursor, LOTE)).isEmpty()) {
        cursor = pagina.getLast();
        List<UUID> orfas = orfasDa(pagina);
        if (!orfas.isEmpty()) {
          orfas.forEach(id -> armazem.apagar(CATEGORIA, id));
          return orfas.size();
        }
      }
      return 0;
    }
  }
}
