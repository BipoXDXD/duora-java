package bipotheking.net.duora.room;

import bipotheking.net.duora.game.Assento;
import java.time.Clock;
import java.util.Comparator;
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
   * Cria a sala de A. Sincronizado para que a contagem e o registro sejam um passo só. No teto de
   * memória, a lápide mais antiga é descartada para abrir espaço.
   */
  synchronized Entrada criar(Apelido apelido) {
    if (todas.size() >= limites.maximoTotal()) {
      descartarLapideMaisAntiga();
    }
    long ativas = todas.stream().filter(Sala::ativa).count();
    if (ativas >= limites.maximoSalas() || todas.size() >= limites.maximoTotal()) {
      throw new ErroApi(Codigo.LIMITE_SALAS, "Há terraços demais abertos agora. Tente mais tarde.");
    }
    String token = Segredos.novoToken(Segredos.BYTES_ASSENTO);
    Convite convite = Convite.novo();
    Participante criador = new Participante(Assento.A, apelido, Segredos.resumo(token));
    Sala sala = new Sala(Segredos.novoIdDeSala(), criador, convite, relogio, limites);
    todas.add(sala);
    porAssento.put(criador.resumoToken(), sala);
    porConvite.put(convite.resumo(), sala);
    LOG.info("Sala {} criada ({} ativas, {} no total)", sala.id(), ativas + 1, todas.size());
    return new Entrada(token, sala.consultar(Assento.A));
  }

  /** O convite é de uso único: sai do índice assim que B entra. */
  Entrada entrar(String convite, Apelido apelido) {
    Sala sala =
        Optional.ofNullable(convite)
            .map(c -> porConvite.get(Segredos.resumo(c)))
            .orElseThrow(() -> new ErroApi(Codigo.CONVITE_INVALIDO, "Este convite não vale."));
    String token = Segredos.novoToken(Segredos.BYTES_ASSENTO);
    Participante parceiro = new Participante(Assento.B, apelido, Segredos.resumo(token));
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
