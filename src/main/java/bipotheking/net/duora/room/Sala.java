package bipotheking.net.duora.room;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.game.Assento;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.Decisao;
import bipotheking.net.duora.game.Estado;
import bipotheking.net.duora.game.Lugar;
import bipotheking.net.duora.game.Peca;
import bipotheking.net.duora.game.Recusa;
import bipotheking.net.duora.game.Regras;
import bipotheking.net.duora.sala.CodigoSala;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
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
  private final Instant abertaEm;
  private final @Nullable Participante reservado;
  private final Set<Assento> reivindicados = EnumSet.noneOf(Assento.class);
  private final ReentrantLock trava = new ReentrantLock();
  private volatile Retrato retrato;

  /** Sala do terraço anônimo: A sentado, à espera de quem usar o convite. */
  Sala(
      String id,
      Participante criador,
      Convite convite,
      Cenario cenario,
      Clock relogio,
      LimitesSala limites) {
    this(id, convite.resumo(), cenario, relogio, limites, null, criador, Optional.of(convite));
  }

  private Sala(
      String id,
      String resumoConvite,
      Cenario cenario,
      Clock relogio,
      LimitesSala limites,
      @Nullable Participante reservado,
      Participante criador,
      Optional<Convite> convite) {
    this.id = id;
    this.resumoConvite = resumoConvite;
    this.cenario = cenario;
    this.relogio = relogio;
    this.limites = limites;
    this.abertaEm = relogio.instant();
    this.reservado = reservado;
    this.retrato =
        convite
            .map(c -> Retrato.aberto(criador, c, abertaEm))
            .orElseGet(() -> Retrato.reservado(criador, abertaEm));
  }

  /**
   * Sala ligada a duas contas: A sentado e B reservado, os dois com tokens descartados até
   * reivindicarem o assento. Sem convite; o resumo da visão vem de um convite que ninguém recebe.
   */
  static Sala pareada(
      String id,
      Participante criador,
      Participante reservado,
      Cenario cenario,
      Clock relogio,
      LimitesSala limites) {
    return new Sala(
        id,
        Convite.novo().resumo(),
        cenario,
        relogio,
        limites,
        reservado,
        criador,
        Optional.empty());
  }

  /** O token novo de quem reivindicou e o resumo do anterior, que sai do índice. */
  record Reivindicacao(Optional<String> resumoAnterior, Visao visao) {}

  String id() {
    return id;
  }

  Instant abertaEm() {
    return abertaEm;
  }

  /** Ligada a contas (pareada), e não do terraço anônimo. */
  boolean pareada() {
    return reservado != null;
  }

  /** Se a conta tem assento nesta sala, reivindicado ou não. */
  boolean daConta(ContaId conta) {
    return assentoDaConta(retrato, conta).isPresent();
  }

  /** Se a conta já reivindicou o assento dela. */
  boolean presente(ContaId conta) {
    trava.lock();
    try {
      return assentoDaConta(retrato, conta).map(reivindicados::contains).orElse(false);
    } finally {
      trava.unlock();
    }
  }

  /**
   * Troca o token do assento da conta pelo do resumo dado; na primeira vez de B, B entra no jogo.
   * Sala encerrada ou conta de fora: {@code ENCONTRO_INEXISTENTE}.
   */
  Reivindicacao reivindicar(ContaId conta, String resumoNovo) {
    Instant agora;
    Retrato atual;
    Assento assento;
    Optional<String> anterior;
    trava.lock();
    try {
      agora = relogio.instant();
      atual = retratoVigente(agora);
      if (atual.encerramento().isPresent()) {
        throw CodigoSala.ENCONTRO_INEXISTENTE.erro();
      }
      assento = assentoDaConta(atual, conta).orElseThrow(CodigoSala.ENCONTRO_INEXISTENTE::erro);
      atual = comAberturaAvancada(atual, agora);
      Optional<Participante> sentado = atual.participante(assento);
      anterior = sentado.map(Participante::resumoToken);
      if (sentado.isPresent()) {
        atual = atual.comResumo(assento, resumoNovo).comContato(assento, agora);
      } else {
        atual = comBReservado(atual, resumoNovo, agora);
      }
      retrato = atual;
      reivindicados.add(assento);
    } finally {
      trava.unlock();
    }
    LOG.info("Sala {}: assento {} reivindicado", id, assento);
    return new Reivindicacao(
        anterior, Visao.de(cenario, atual, resumoConvite, assento, agora, limites, encontroId()));
  }

  /** B reservado entra no jogo com o token novo. Chamar com o lock. */
  private Retrato comBReservado(Retrato atual, String resumoNovo, Instant agora) {
    Participante vaga = Objects.requireNonNull(reservado);
    Participante parceiro =
        new Participante(vaga.assento(), vaga.apelido(), resumoNovo, vaga.conta());
    Estado estado = aceitoOuFalha(Regras.parceiroEntrou(cenario, atual.estado(), agora));
    return comAberturaAvancada(atual.comParceiro(parceiro, estado, agora), agora);
  }

  private Optional<Assento> assentoDaConta(Retrato atual, ContaId conta) {
    Optional<Assento> sentado =
        atual.participantes().values().stream()
            .filter(p -> conta.equals(p.conta()))
            .map(Participante::assento)
            .findFirst();
    if (sentado.isPresent() || reservado == null || !conta.equals(reservado.conta())) {
      return sentado;
    }
    return Optional.of(reservado.assento());
  }

  /** O id que a visão expõe: só a sala pareada tem encontro. */
  private @Nullable String encontroId() {
    return pareada() ? id : null;
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

  /** Consulta: registra presença e avança a abertura pelo tempo; não renova a expiração. */
  Visao consultar(Assento quem) {
    Instant agora;
    Retrato atual;
    trava.lock();
    try {
      agora = relogio.instant();
      atual = comAberturaAvancada(exigirAberta(agora), agora).comContato(quem, agora);
      retrato = atual;
    } finally {
      trava.unlock();
    }
    return Visao.de(cenario, atual, resumoConvite, quem, agora, limites, encontroId());
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
      atual = comAberturaAvancada(atual, agora);
      Estado estado = aceitoOuFalha(Regras.parceiroEntrou(cenario, atual.estado(), agora));
      atual = comAberturaAvancada(atual.comParceiro(parceiro, estado, agora), agora);
      retrato = atual;
    } finally {
      trava.unlock();
    }
    LOG.info("Sala {}: parceiro entrou", id);
    return Visao.de(
        cenario, atual, resumoConvite, parceiro.assento(), agora, limites, encontroId());
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

  Visao tocarAbertura(Assento quem) {
    return agir(quem, (estado, agora) -> Regras.tocarAbertura(cenario, estado, quem, agora));
  }

  /** Encerra a sala para as duas pessoas. Sair de sala já encerrada não muda nada. */
  void sair() {
    encerrar(MotivoEncerramento.SAIU);
  }

  /** Encerra sem jogo (ninguém chegou no prazo). Cancelar sala já encerrada não muda nada. */
  void cancelar() {
    encerrar(MotivoEncerramento.CANCELADA);
  }

  private void encerrar(MotivoEncerramento motivo) {
    trava.lock();
    try {
      Instant agora = relogio.instant();
      Retrato atual = retratoVigente(agora);
      if (atual.encerramento().isEmpty()) {
        retrato = atual.encerrado(motivo, agora);
        LOG.info("Sala {} encerrada: {}", id, motivo);
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

  /**
   * Aplica a regra sob o lock; o instante é lido já com o lock, para nunca voltar no tempo. A
   * abertura avança antes da regra (que vê a abertura vigente) e depois dela.
   */
  private Visao agir(Assento quem, BiFunction<Estado, Instant, Decisao> regra) {
    Instant agora;
    Retrato atual;
    trava.lock();
    try {
      agora = relogio.instant();
      atual = comAberturaAvancada(exigirAberta(agora), agora).comContato(quem, agora);
      retrato = atual;
      switch (regra.apply(atual.estado(), agora)) {
        case Decisao.Aceita(Estado novo) -> {
          atual = comAberturaAvancada(atual.comAcao(novo, agora), agora);
          retrato = atual;
        }
        case Decisao.Recusada(Recusa recusa) -> throw recusar(recusa, atual, quem, agora);
      }
    } finally {
      trava.unlock();
    }
    return Visao.de(cenario, atual, resumoConvite, quem, agora, limites, encontroId());
  }

  private ErroApi recusar(Recusa recusa, Retrato atual, Assento quem, Instant agora) {
    return ErroApi.comVisao(
        Codigo.de(recusa),
        detalhe(recusa, atual, quem),
        Visao.de(cenario, atual, resumoConvite, quem, agora, limites, encontroId()));
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

  /**
   * Retrato com a abertura concluída pelo tempo, se chegou ao máximo. Avançar não é ação: não
   * renova a expiração. Chamar com o lock.
   */
  private Retrato comAberturaAvancada(Retrato atual, Instant agora) {
    Estado avancado = Regras.avancarAbertura(cenario, atual.estado(), agora);
    return avancado == atual.estado() ? atual : atual.comEstado(avancado);
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
