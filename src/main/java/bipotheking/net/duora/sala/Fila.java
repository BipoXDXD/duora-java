package bipotheking.net.duora.sala;

import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.eventos.CanalDeEventos;
import bipotheking.net.duora.eventos.Evento;
import bipotheking.net.duora.pareamento.Candidato;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.Localizacoes;
import bipotheking.net.duora.perfil.PerfilDeFila;
import bipotheking.net.duora.perfil.Perfis;
import bipotheking.net.duora.sala.dominio.EntradaNaFila;
import bipotheking.net.duora.sala.dominio.EstimativaDeVaga;
import bipotheking.net.duora.sala.dominio.RepositorioDaFila;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A fila contínua dos encontros públicos: entrar, ver o estado e desistir. Quem entra fica até
 * desistir, ser pareado e entrar no encontro, ou sumir da tela por tempo demais (o ciclo cuida dos
 * dois últimos). A fila não guarda preferência nem célula: o ciclo lê o perfil de novo a cada vez.
 *
 * <p>As portas dos encontros entram {@code @Lazy}: quem as implementa (a ponte da {@code room}) só
 * é procurado na primeira chamada.
 */
@Service
public class Fila {

  /** Regra de limite de taxa da entrada, por conta. */
  static final String LIMITE_POR_CONTA = "fila-entrar";

  static final String EVENTO_ESTADO = "fila.estado";

  private final RepositorioDaFila repositorio;
  private final Perfis perfis;
  private final ConsultaDeContas contas;
  private final Localizacoes localizacoes;
  private final LimiteDeTaxa limiteDeTaxa;
  private final AberturaDeEncontro encontros;
  private final OcupacaoDeEncontros ocupacao;
  private final CanalDeEventos canal;
  private final TransactionTemplate transacao;
  private final ConfigDoPareamento config;
  private final Clock relogio;

  Fila(
      RepositorioDaFila repositorio,
      Perfis perfis,
      ConsultaDeContas contas,
      Localizacoes localizacoes,
      LimiteDeTaxa limiteDeTaxa,
      @Lazy AberturaDeEncontro encontros,
      @Lazy OcupacaoDeEncontros ocupacao,
      CanalDeEventos canal,
      TransactionTemplate transacao,
      ConfigDoPareamento config,
      Clock relogio) {
    this.repositorio = repositorio;
    this.perfis = perfis;
    this.contas = contas;
    this.localizacoes = localizacoes;
    this.limiteDeTaxa = limiteDeTaxa;
    this.encontros = encontros;
    this.ocupacao = ocupacao;
    this.canal = canal;
    this.transacao = transacao;
    this.config = config;
    this.relogio = relogio;
  }

  /**
   * Põe a conta na fila e publica o primeiro {@code fila.estado}, na mesma transação. Quem já está
   * recebe o estado atual, sem mudar nada. A coordenada, se veio, troca a célula de quem consentiu
   * a localização; sem consentimento, é descartada e a pessoa entra mesmo assim.
   *
   * @throws bipotheking.net.duora.web.LimiteExcedido mais de 10 entradas no minuto
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code COORDENADA_INVALIDA} (fora do globo),
   *     {@code CONTA_PAUSADA}, {@code PERFIL_INCOMPLETO} ou {@code JA_EM_ENCONTRO}
   */
  public EstadoNaFila entrar(ContaId conta, Optional<Coordenadas> coordenadas) {
    limiteDeTaxa.consumir(LIMITE_POR_CONTA, conta.toString());
    coordenadas.ifPresent(ponto -> CelulaGeohash.de(ponto.latitude(), ponto.longitude()));
    Optional<EstadoNaFila> atual = estado(conta);
    if (atual.isPresent()) {
      return atual.get();
    }
    Perfis.Prontidao prontidao = perfis.prontidao(conta);
    if (prontidao.pausada()) {
      throw CodigoSala.CONTA_PAUSADA.erro();
    }
    if (!prontidao.prontoParaFila()) {
      throw CodigoSala.PERFIL_INCOMPLETO.erro();
    }
    if (encontros.encontroAtivoDe(conta).isPresent()) {
      throw CodigoSala.JA_EM_ENCONTRO.erro();
    }
    if (coordenadas.isPresent() && localizacoes.consentida(conta)) {
      Coordenadas ponto = coordenadas.get();
      localizacoes.atualizar(conta, ponto.latitude(), ponto.longitude());
    }
    boolean deTeste =
        contas
            .porId(conta)
            .map(ContaResumo::deTeste)
            .orElseThrow(() -> new IllegalStateException("Conta pronta sem resumo: " + conta));
    return Objects.requireNonNull(transacao.execute(_ -> inserir(conta, deTeste)));
  }

  private EstadoNaFila inserir(ContaId conta, boolean deTeste) {
    boolean entrou = repositorio.inserir(conta, relogio.instant(), deTeste);
    // Sem a linha aqui, uma desistência correu entre a inserção concorrente e esta leitura.
    EstadoNaFila estado = estado(conta).orElseThrow(CodigoSala.FORA_DA_FILA::erro);
    if (entrou) {
      canal.publicar(conta, new Evento(EVENTO_ESTADO, estado));
    }
    return estado;
  }

  /** Tira a conta da fila; fora dela, não faz nada. */
  public void desistir(ContaId conta) {
    repositorio.remover(conta);
  }

  /** O estado da conta na fila; vazio se ela não está. */
  public Optional<EstadoNaFila> estado(ContaId conta) {
    return repositorio.daConta(conta).map(this::estadoDe);
  }

  /**
   * Quem ainda espera e entrou até {@code limite}, por ordem de chegada, com o perfil lido agora.
   * Quem deixou de estar pronto para a fila fica de fora.
   */
  public List<Candidato> esperandoDesde(Instant limite) {
    List<EntradaNaFila> entradas = repositorio.esperandoDesde(limite);
    Map<ContaId, PerfilDeFila> prontos =
        perfis.paraFila(entradas.stream().map(EntradaNaFila::conta).toList());
    List<Candidato> candidatos = new ArrayList<>();
    for (EntradaNaFila entrada : entradas) {
      PerfilDeFila perfil = prontos.get(entrada.conta());
      if (perfil != null) {
        candidatos.add(Candidato.de(entrada.conta(), entrada.entrouEm(), perfil));
      }
    }
    return List.copyOf(candidatos);
  }

  private EstadoNaFila estadoDe(EntradaNaFila entrada) {
    if (entrada.pareada()) {
      return EstadoNaFila.pareado(
          Objects.requireNonNull(entrada.pareadoEm()),
          Objects.requireNonNull(entrada.encontroId()),
          Objects.requireNonNull(entrada.cenarioId()));
    }
    if (ocupacao.ativos() < config.maximoSimultaneos()) {
      return EstadoNaFila.aguardandoCompativel(entrada.entrouEm());
    }
    int posicao = repositorio.posicaoDe(entrada.conta());
    OptionalInt estimativa =
        EstimativaDeVaga.minutos(posicao, ocupacao.terminosPrevistos(), relogio.instant());
    return EstadoNaFila.aguardandoVaga(
        entrada.entrouEm(), posicao, estimativa.isPresent() ? estimativa.getAsInt() : null);
  }
}
