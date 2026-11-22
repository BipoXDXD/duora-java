package bipotheking.net.duora.room;

import bipotheking.net.duora.game.Assento;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.Decisao;
import bipotheking.net.duora.game.Estado;
import bipotheking.net.duora.game.Lugar;
import bipotheking.net.duora.game.Peca;
import bipotheking.net.duora.game.Recusa;
import bipotheking.net.duora.game.Regras;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uma sala com duas pessoas. Toda mudança acontece sob o lock da sala e publica um {@link Retrato}
 * novo; leituras usam o retrato publicado.
 */
final class Sala {

  private static final Logger LOG = LoggerFactory.getLogger(Sala.class);

  private final String id;
  private final String resumoConvite;
  private final Cenario cenario;
  private final Clock relogio;
  private final LimitesSala limites;
  private final ReentrantLock trava = new ReentrantLock();
  private volatile Retrato retrato;

  Sala(
      String id,
      Participante criador,
      Convite convite,
      Cenario cenario,
      Clock relogio,
      LimitesSala limites) {
    this.id = id;
    this.resumoConvite = convite.resumo();
    this.cenario = cenario;
    this.relogio = relogio;
    this.limites = limites;
    this.retrato = Retrato.aberto(criador, convite, relogio.instant());
  }

  String id() {
    return id;
  }

  /** Fixo desde a criação; lido sem a trava. */
  Cenario cenario() {
    return cenario;
  }

  /** Chave da sala no índice de convites; guardada para a remoção ser direta. */
  String resumoConvite() {
    return resumoConvite;
  }

  /** Chaves da sala no índice de assentos. */
  List<String> resumosDeAssento() {
    return retrato.participantes().values().stream().map(Participante::resumoToken).toList();
  }

  /** Momento do encerramento, se a sala já é lápide. */
  Optional<Instant> encerradaEm() {
    return retrato.encerramento().map(Encerramento::em);
  }

  Optional<Assento> assentoDe(String resumoToken) {
    return retrato.participantes().values().stream()
        .filter(p -> p.resumoToken().equals(resumoToken))
        .map(Participante::assento)
        .findFirst();
  }

  /** Aberta e dentro do prazo de inatividade. */
  boolean ativa() {
    Retrato atual = retrato;
    return atual.encerramento().isEmpty() && relogio.instant().isBefore(atual.expiraEm(limites));
  }

  /** Lápide vencida: a sala pode ser esquecida. */
  boolean descartavel() {
    Instant agora = relogio.instant();
    return retrato
        .encerramento()
        .map(e -> !agora.isBefore(e.em().plus(limites.lapide())))
        .orElse(false);
  }

  /** Consulta: registra presença, não renova a expiração. */
  Visao consultar(Assento quem) {
    Instant agora;
    Retrato atual;
    trava.lock();
    try {
      agora = relogio.instant();
      atual = exigirAberta(agora).comContato(quem, agora);
      retrato = atual;
    } finally {
      trava.unlock();
    }
    return Visao.de(cenario, atual, quem, agora, limites);
  }

  /** Convite já usado ou de sala encerrada não vale mais. */
  Visao entrar(Participante parceiro) {
    Instant agora;
    Retrato atual;
    trava.lock();
    try {
      agora = relogio.instant();
      atual = retratoVigente(agora);
      if (atual.encerramento().isPresent() || atual.participante(parceiro.assento()).isPresent()) {
        throw new ErroApi(Codigo.CONVITE_INVALIDO, "Este convite não vale mais.");
      }
      Estado estado = aceitoOuFalha(Regras.parceiroEntrou(cenario, atual.estado(), agora));
      atual = atual.comParceiro(parceiro, estado, agora);
      retrato = atual;
    } finally {
      trava.unlock();
    }
    LOG.info("Sala {}: parceiro entrou", id);
    return Visao.de(cenario, atual, parceiro.assento(), agora, limites);
  }

  Visao colocar(Assento quem, Peca peca, Optional<Lugar> destino, long versaoEsperada) {
    return agir(
        quem,
        (estado, agora) -> Regras.colocar(cenario, estado, quem, peca, destino, versaoEsperada));
  }

  Visao sinalizar(Assento quem, Peca peca, Lugar destino) {
    return agir(
        quem, (estado, agora) -> Regras.sinalizar(cenario, estado, quem, peca, destino, agora));
  }

  Visao confirmar(Assento quem, boolean pronto, long versao) {
    return agir(quem, (estado, agora) -> Regras.confirmar(cenario, estado, quem, pronto, versao));
  }

  Visao pedirAjuste(Assento quem) {
    return agir(quem, (estado, agora) -> Regras.pedirAjuste(cenario, estado, quem));
  }

  Visao recusarAjuste(Assento quem) {
    return agir(quem, (estado, agora) -> Regras.recusarAjuste(cenario, estado, quem));
  }

  /** Encerra a sala para as duas pessoas. Sair de sala já encerrada não muda nada. */
  void sair() {
    trava.lock();
    try {
      Instant agora = relogio.instant();
      Retrato atual = retratoVigente(agora);
      if (atual.encerramento().isEmpty()) {
        retrato = atual.encerrado(MotivoEncerramento.SAIU, agora);
        LOG.info("Sala {} encerrada: {}", id, MotivoEncerramento.SAIU);
      }
    } finally {
      trava.unlock();
    }
  }

  /** Usada pela varredura: aplica a expiração por inatividade. */
  void expirarSeInativa() {
    trava.lock();
    try {
      retratoVigente(relogio.instant());
    } finally {
      trava.unlock();
    }
  }

  /** Aplica a regra sob o lock; o instante é lido já com o lock, para nunca voltar no tempo. */
  private Visao agir(Assento quem, BiFunction<Estado, Instant, Decisao> regra) {
    Instant agora;
    Retrato atual;
    trava.lock();
    try {
      agora = relogio.instant();
      atual = exigirAberta(agora).comContato(quem, agora);
      retrato = atual;
      switch (regra.apply(atual.estado(), agora)) {
        case Decisao.Aceita(Estado novo) -> {
          atual = atual.comAcao(novo, agora);
          retrato = atual;
        }
        case Decisao.Recusada(Recusa recusa) -> throw recusar(recusa, atual, quem, agora);
      }
    } finally {
      trava.unlock();
    }
    return Visao.de(cenario, atual, quem, agora, limites);
  }

  private ErroApi recusar(Recusa recusa, Retrato atual, Assento quem, Instant agora) {
    return ErroApi.comVisao(
        Codigo.de(recusa),
        detalhe(recusa, atual, quem),
        Visao.de(cenario, atual, quem, agora, limites));
  }

  private static String detalhe(Recusa recusa, Retrato atual, Assento quem) {
    return switch (recusa) {
      case LUGAR_OCUPADO ->
          atual
              .participante(quem.parceiro())
              .map(p -> p.apelido().valor() + " acabou de usar este lugar.")
              .orElse("Este lugar já está ocupado.");
      case PECA_ALHEIA -> "Esta peça não é sua.";
      case LUGAR_INVALIDO -> "Esta peça não vai nesse lugar.";
      case VERSAO_OBSOLETA -> "O terraço mudou enquanto você decidia.";
      case ESPERE_PRIMEIRA_PECA -> "Espere a outra pessoa colocar a primeira peça.";
      case FASE_INVALIDA -> "Isso não pode ser feito agora.";
      case INCOMPLETA -> "A composição ainda não está completa.";
      case ABERTURA_EM_ANDAMENTO -> "Espere as lanternas acenderem.";
    };
  }

  /** Retrato com a expiração por inatividade aplicada. Chamar com o lock. */
  private Retrato retratoVigente(Instant agora) {
    Retrato atual = retrato;
    if (atual.encerramento().isEmpty() && !agora.isBefore(atual.expiraEm(limites))) {
      atual = atual.encerrado(MotivoEncerramento.EXPIROU, atual.expiraEm(limites));
      retrato = atual;
      LOG.info("Sala {} encerrada: {}", id, MotivoEncerramento.EXPIROU);
    }
    return atual;
  }

  /** Retrato vigente de sala aberta; lápide vira 410 e lápide vencida, 404. Chamar com o lock. */
  private Retrato exigirAberta(Instant agora) {
    Retrato atual = retratoVigente(agora);
    Optional<Encerramento> encerramento = atual.encerramento();
    if (encerramento.isEmpty()) {
      return atual;
    }
    if (!agora.isBefore(encerramento.get().em().plus(limites.lapide()))) {
      throw new ErroApi(Codigo.SALA_INEXISTENTE, "Este terraço não existe mais.");
    }
    throw ErroApi.encerrada(encerramento.get().motivo());
  }

  private static Estado aceitoOuFalha(Decisao decisao) {
    return switch (decisao) {
      case Decisao.Aceita(Estado estado) -> estado;
      case Decisao.Recusada(Recusa recusa) ->
          throw new IllegalStateException("Regra recusou entrada já validada: " + recusa);
    };
  }
}
