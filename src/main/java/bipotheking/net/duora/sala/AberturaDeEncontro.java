package bipotheking.net.duora.sala;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.ParPareado;
import java.util.Optional;

/**
 * Porta de {@code sala} para quem hospeda os encontros (a {@code room} até a Etapa 5). Os ids de
 * encontro e os convites são opacos para {@code sala}.
 */
public interface AberturaDeEncontro {

  /** Abre o encontro do par que a fila formou, no cenário dado. */
  EncontroAberto abrirPareado(ParPareado par, String cenarioId);

  /** Abre um encontro privado à espera de quem receber o convite. */
  ConvitePrivado abrirPrivado(ContaId criador, String cenarioId);

  /** Põe o convidado no encontro do convite, que deixa de valer. */
  EncontroAberto entrarPrivado(String convite, ContaId convidado);

  /** Quem criou o convite, enquanto ele estiver aberto; usado, encerrado ou inexistente, vazio. */
  Optional<ContaId> criadorDoConvite(String convite);

  /** O encontro em andamento da conta, se houver. */
  Optional<String> encontroAtivoDe(ContaId conta);

  /** Encerra o encontro sem jogo (ex.: ninguém entrou no prazo). */
  void cancelar(String encontroId);

  /** Se a conta já ocupou o assento dela no encontro. */
  boolean presente(String encontroId, ContaId conta);
}
