package bipotheking.net.duora.room;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.game.Assento;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.sala.CodigoSala;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registro das salas em memória, indexado pelo resumo SHA-256 dos tokens de assento e de convite.
 */
@Component
class Salas {

  private static final Logger LOG = LoggerFactory.getLogger(Salas.class);

  private final Clock relogio;
  private final LimitesSala limites;
  private final Set<Sala> todas = ConcurrentHashMap.newKeySet();
  private final Map<String, Sala> porAssento = new ConcurrentHashMap<>();
  private final Map<String, Sala> porConvite = new ConcurrentHashMap<>();

  Salas(Clock relogio, LimitesSala limites) {
    this.relogio = relogio;
    this.limites = limites;
  }

  /** Pessoa já sentada numa sala, identificada pelo cookie. */
  record Assentado(Sala sala, Assento assento) {}

  /** Resultado de criar ou entrar: o token vai só para o cookie. */
  record Entrada(String token, Visao visao) {
    @Override
    public String toString() {
      return "Entrada[token=***]";
    }
  }

  /**
   * Cria a sala de A no cenário dado. Sincronizado para que a contagem e o registro sejam um passo
   * só. No teto de memória, a lápide mais antiga é descartada para abrir espaço.
   */
  synchronized Entrada criar(Apelido apelido, Cenario cenario) {
    if (todas.size() >= limites.maximoTotal()) {
      descartarLapideMaisAntiga();
    }
    long ativas = todas.stream().filter(sala -> !sala.pareada() && sala.ativa()).count();
    if (ativas >= limites.maximoSalas() || todas.size() >= limites.maximoTotal()) {
      throw new ErroApi(Codigo.LIMITE_SALAS, "Há terraços demais abertos agora. Tente mais tarde.");
    }
    String token = Segredos.novoToken(Segredos.BYTES_ASSENTO);
    Convite convite = Convite.novo();
    Participante criador = Participante.anonimo(Assento.A, apelido, Segredos.resumo(token));
    Sala sala = new Sala(Segredos.novoIdDeSala(), criador, convite, cenario, relogio, limites);
    todas.add(sala);
    porAssento.put(criador.resumoToken(), sala);
    porConvite.put(convite.resumo(), sala);
    LOG.info("Sala {} criada ({} ativas, {} no total)", sala.id(), ativas + 1, todas.size());
    return new Entrada(token, sala.consultar(Assento.A));
  }

  /**
   * Cria a sala de duas contas: A criadora, B reservado, os dois com tokens descartados até
   * reivindicarem. Conta só no teto de memória, não nas vagas do terraço anônimo, e fica fora do
   * índice de convites.
   */
  synchronized String criarPareada(
      ContaId a, Apelido apelidoA, ContaId b, Apelido apelidoB, Cenario cenario) {
    if (todas.size() >= limites.maximoTotal()) {
      descartarLapideMaisAntiga();
    }
    if (todas.size() >= limites.maximoTotal()) {
      throw new ErroApi(Codigo.LIMITE_SALAS, "Há terraços demais abertos agora. Tente mais tarde.");
    }
    Participante criador = new Participante(Assento.A, apelidoA, resumoDescartado(), a);
    Participante reservado = new Participante(Assento.B, apelidoB, resumoDescartado(), b);
    Sala sala =
        Sala.pareada(Segredos.novoIdDeSala(), criador, reservado, cenario, relogio, limites);
    todas.add(sala);
    LOG.info("Sala {} pareada criada ({} no total)", sala.id(), todas.size());
    return sala.id();
  }

  /**
   * Emite um token novo para o assento da conta; o anterior deixa de valer. Sincronizado para a
   * troca no índice acompanhar a ordem das trocas na sala.
   */
  synchronized Entrada reivindicar(String salaId, ContaId conta) {
    Sala sala = porId(salaId).orElseThrow(CodigoSala.ENCONTRO_INEXISTENTE::erro);
    String token = Segredos.novoToken(Segredos.BYTES_ASSENTO);
    String resumo = Segredos.resumo(token);
    Sala.Reivindicacao reivindicacao = sala.reivindicar(conta, resumo);
    reivindicacao.resumoAnterior().ifPresent(anterior -> porAssento.remove(anterior, sala));
    porAssento.put(resumo, sala);
    return new Entrada(token, reivindicacao.visao());
  }

  /** A sala pareada ativa da conta, se houver. */
  Optional<String> salaAtivaDe(ContaId conta) {
    return pareadasAtivas().stream().filter(sala -> sala.daConta(conta)).map(Sala::id).findFirst();
  }

  /** Se a conta já reivindicou o assento dela na sala. */
  boolean presente(String salaId, ContaId conta) {
    return porId(salaId).map(sala -> sala.presente(conta)).orElse(false);
  }

  /** Encerra a sala sem jogo; id desconhecido não muda nada. */
  void cancelar(String salaId) {
    porId(salaId).ifPresent(Sala::cancelar);
  }

  /** Salas ligadas a contas que ainda estão ativas. */
  List<Sala> pareadasAtivas() {
    return todas.stream().filter(sala -> sala.pareada() && sala.ativa()).toList();
  }

  private Optional<Sala> porId(String salaId) {
    return todas.stream().filter(sala -> sala.id().equals(salaId)).findFirst();
  }

  private static String resumoDescartado() {
    return Segredos.resumo(Segredos.novoToken(Segredos.BYTES_ASSENTO));
  }

  /** O convite é de uso único: sai do índice assim que B entra. */
  Entrada entrar(String convite, Apelido apelido) {
    Sala sala =
        Optional.ofNullable(convite)
            .map(c -> porConvite.get(Segredos.resumo(c)))
            .orElseThrow(() -> new ErroApi(Codigo.CONVITE_INVALIDO, "Este convite não vale."));
    String token = Segredos.novoToken(Segredos.BYTES_ASSENTO);
    Participante parceiro = Participante.anonimo(Assento.B, apelido, Segredos.resumo(token));
    Visao visao = sala.entrar(parceiro);
    porConvite.remove(sala.resumoConvite(), sala);
    porAssento.put(parceiro.resumoToken(), sala);
    return new Entrada(token, visao);
  }

  Assentado localizar(Optional<String> token) {
    String resumo =
        Segredos.resumo(
            token.orElseThrow(
                () -> new ErroApi(Codigo.SEM_ASSENTO, "Entre num terraço para continuar.")));
    Sala sala = porAssento.get(resumo);
    Optional<Assento> assento = Optional.ofNullable(sala).flatMap(s -> s.assentoDe(resumo));
    if (assento.isEmpty()) {
      throw new ErroApi(Codigo.SALA_INEXISTENTE, "Este terraço não existe mais.");
    }
    return new Assentado(sala, assento.get());
  }

  /** Salas guardadas em memória: ativas e lápides. */
  int total() {
    return todas.size();
  }

  /** Expira salas inativas e esquece lápides vencidas. */
  synchronized void varrer() {
    for (Sala sala : todas) {
      sala.expirarSeInativa();
      if (sala.descartavel()) {
        esquecer(sala);
      }
    }
  }

  private void descartarLapideMaisAntiga() {
    todas.forEach(Sala::expirarSeInativa);
    todas.stream()
        .filter(sala -> sala.encerradaEm().isPresent())
        .min(Comparator.comparing(sala -> sala.encerradaEm().orElseThrow()))
        .ifPresent(this::esquecer);
  }

  /** Remoção direta pelas chaves que a própria sala guarda. */
  private void esquecer(Sala sala) {
    todas.remove(sala);
    sala.resumosDeAssento().forEach(resumo -> porAssento.remove(resumo, sala));
    porConvite.remove(sala.resumoConvite(), sala);
    LOG.info("Sala {} removida", sala.id());
  }
}
