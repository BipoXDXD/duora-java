package bipotheking.net.duora.sala;

import java.time.Instant;
import java.util.List;

/** Quantos encontros ocupam vaga agora e quando os atuais devem terminar. */
public interface OcupacaoDeEncontros {

  /** Encontros pareados ativos mais os privados com convidado; convite aberto não ocupa vaga. */
  int ativos();

  /** O término previsto de cada encontro ativo, em ordem crescente. */
  List<Instant> terminosPrevistos();
}
