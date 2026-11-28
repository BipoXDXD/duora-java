package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.FotoId;
import bipotheking.net.duora.perfil.FotoPendente;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Porta da tabela {@code foto}: no máximo uma foto por estado e conta. */
public interface RepositorioDeFotos {

  /**
   * Trava consultiva das fotos da conta até o fim da transação: envio, moderação e exclusão da
   * mesma conta passam um de cada vez. Exige transação aberta.
   */
  void travarConta(ContaId conta);

  Optional<FotoDePerfil> porId(FotoId id);

  List<FotoDePerfil> daConta(ContaId conta);

  /** Apaga a foto da conta no estado, se houver, e devolve o id dela. */
  Optional<FotoId> apagarDoEstado(ContaId conta, EstadoDaFoto estado);

  /** Grava a foto pendente; a conta não pode ter outra pendente. */
  void inserirPendente(FotoDePerfil foto);

  /**
   * Leva a foto pendente ao estado decidido; falso se ela não existe ou já não está pendente.
   *
   * @throws IllegalArgumentException se {@code estado} for {@code PENDENTE}
   */
  boolean decidir(FotoId id, EstadoDaFoto estado, Instant agora);

  /** Até {@code limite} pendentes, das mais antigas para as mais novas. */
  List<FotoPendente> pendentes(int limite);

  /** Apaga todas as fotos da conta e devolve os ids delas. */
  List<FotoId> apagarDaConta(ContaId conta);

  /** Quais destes ids ainda têm linha na tabela. */
  Set<UUID> existentes(Collection<UUID> ids);
}
