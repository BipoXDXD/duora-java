package bipotheking.net.duora.room;

import bipotheking.net.duora.game.Assento;
import bipotheking.net.duora.game.Estado;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fotografia imutável de uma sala. A sala troca a fotografia inteira sob o seu lock; leituras usam
 * a fotografia sem bloquear.
 *
 * @param convite presente só enquanto ninguém entrou
 * @param ultimaAcao última ação válida; base da expiração (consultas não contam)
 * @param ultimoContato última consulta ou ação de cada assento; base da presença
 */
record Retrato(
    Estado estado,
    Map<Assento, Participante> participantes,
    Optional<Convite> convite,
    Instant ultimaAcao,
    Map<Assento, Instant> ultimoContato,
    Optional<Encerramento> encerramento) {

  Retrato {
    participantes = Map.copyOf(participantes);
    ultimoContato = Map.copyOf(ultimoContato);
  }

  static Retrato aberto(Participante criador, Convite convite, Instant agora) {
    return new Retrato(
        Estado.inicial(),
        Map.of(criador.assento(), criador),
        Optional.of(convite),
        agora,
        Map.of(criador.assento(), agora),
        Optional.empty());
  }

  /** Sala pareada: só o criador sentado e nenhum convite. */
  static Retrato reservado(Participante criador, Instant agora) {
    return new Retrato(
        Estado.inicial(),
        Map.of(criador.assento(), criador),
        Optional.empty(),
        agora,
        Map.of(),
        Optional.empty());
  }

  Optional<Participante> participante(Assento assento) {
    return Optional.ofNullable(participantes.get(assento));
  }

  Retrato comContato(Assento assento, Instant agora) {
    Map<Assento, Instant> contatos = new EnumMap<>(Assento.class);
    contatos.putAll(ultimoContato);
    contatos.put(assento, agora);
    return new Retrato(estado, participantes, convite, ultimaAcao, contatos, encerramento);
  }

  /** Troca o estado sem contar como ação: a expiração continua da última ação. */
  Retrato comEstado(Estado novo) {
    return new Retrato(novo, participantes, convite, ultimaAcao, ultimoContato, encerramento);
  }

  Retrato comAcao(Estado novo, Instant agora) {
    return new Retrato(novo, participantes, convite, agora, ultimoContato, encerramento);
  }

  Retrato comParceiro(Participante parceiro, Estado novo, Instant agora) {
    Map<Assento, Participante> todos = new EnumMap<>(Assento.class);
    todos.putAll(participantes);
    todos.put(parceiro.assento(), parceiro);
    return new Retrato(novo, todos, Optional.empty(), agora, ultimoContato, encerramento)
        .comContato(parceiro.assento(), agora);
  }

  /** Troca o resumo do token de um assento já ocupado: o token anterior deixa de valer. */
  Retrato comResumo(Assento assento, String resumoToken) {
    Participante atual = participantes.get(assento);
    Map<Assento, Participante> todos = new EnumMap<>(Assento.class);
    todos.putAll(participantes);
    todos.put(assento, new Participante(assento, atual.apelido(), resumoToken, atual.conta()));
    return new Retrato(estado, todos, convite, ultimaAcao, ultimoContato, encerramento);
  }

  Retrato encerrado(MotivoEncerramento motivo, Instant em) {
    return new Retrato(
        estado,
        participantes,
        Optional.empty(),
        ultimaAcao,
        ultimoContato,
        Optional.of(new Encerramento(motivo, em)));
  }

  boolean conectado(Assento assento, Instant agora, LimitesSala limites) {
    Instant contato = ultimoContato.get(assento);
    return contato != null && agora.isBefore(contato.plus(limites.presenca()));
  }

  Instant expiraEm(LimitesSala limites) {
    return ultimaAcao.plus(limites.inatividade());
  }
}
