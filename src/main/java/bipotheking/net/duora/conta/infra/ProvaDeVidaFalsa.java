package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.CredencialDoNavegador;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.FaixaEstimada;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import bipotheking.net.duora.conta.dominio.ProvaDeVida;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.SessaoDeProva;
import bipotheking.net.duora.conta.dominio.SituacaoDaProva;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Prova de vida de desenvolvimento e e2e, sem fornecedor: responde pelos marcadores da parte local
 * do e-mail da conta dona da sessão ({@code <id>+<marcador>@...}; vários se juntam com {@code +}).
 * {@code idade15} dá a faixa 12–17, {@code idade19} a 17–24 e {@code vidafalha} uma prova que
 * falhou; sem marcador, 30–40 com confiança 99,0. A referência é um JPEG de mentira que carrega o
 * id da sessão, para a {@link ConferenciaDeDocumentoFalsa} achar a conta. A {@link
 * GuardaDaVerificacaoReal} a barra em produção.
 */
@Component
@ConditionalOnProperty(name = "duora.verificacao.provedor", havingValue = "falso")
class ProvaDeVidaFalsa implements ProvaDeVida, CredencialDoNavegador {

  private static final String REGIAO = "us-east-1";
  private static final Duration VALIDADE_DA_CREDENCIAL = Duration.ofMinutes(15);

  /** Início da referência falsa: mágicos de JPEG (JFIF) e um rótulo; depois vem o id da sessão. */
  private static final byte[] PREFIXO_DA_REFERENCIA =
      concatenar(
          new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0},
          "selfie-falsa:".getBytes(StandardCharsets.UTF_8));

  private static final double CONFIANCA = 99.0;
  private static final FaixaEstimada FAIXA_PADRAO = new FaixaEstimada(30, 40);
  private static final FaixaEstimada FAIXA_DE_MENOR = new FaixaEstimada(12, 17);
  private static final FaixaEstimada FAIXA_DE_DUVIDA = new FaixaEstimada(17, 24);
  private static final ResultadoDaProva EXPIRADA =
      new ResultadoDaProva(SituacaoDaProva.EXPIRADA, 0.0, Optional.empty(), Optional.empty());
  private static final ResultadoDaProva FALHOU =
      new ResultadoDaProva(SituacaoDaProva.FALHOU, 0.0, Optional.empty(), Optional.empty());

  private final RepositorioDeVerificacoes verificacoes;
  private final RepositorioDeContas contas;
  private final Clock relogio;

  ProvaDeVidaFalsa(
      RepositorioDeVerificacoes verificacoes, RepositorioDeContas contas, Clock relogio) {
    this.verificacoes = verificacoes;
    this.contas = contas;
    this.relogio = relogio;
  }

  @Override
  public ModoDeCaptura modo() {
    return ModoDeCaptura.FALSO;
  }

  @Override
  public SessaoDeProva criarSessao(UUID tentativa) {
    return new SessaoDeProva("falsa-" + tentativa, REGIAO);
  }

  @Override
  public CredencialCurta emitir(UUID tentativa) {
    return new CredencialCurta(
        "FALSA", "falsa", "falsa", relogio.instant().plus(VALIDADE_DA_CREDENCIAL));
  }

  @Override
  public ResultadoDaProva resultado(String sessaoId) {
    Optional<Conta> conta = contaDaSessao(verificacoes, contas, sessaoId);
    if (conta.isEmpty()) {
      return EXPIRADA;
    }
    Set<String> marcadores = marcadores(conta.get());
    if (marcadores.contains("vidafalha")) {
      return FALHOU;
    }
    return new ResultadoDaProva(
        SituacaoDaProva.CONCLUIDA,
        CONFIANCA,
        Optional.of(faixa(marcadores)),
        Imagem.ler(concatenar(PREFIXO_DA_REFERENCIA, sessaoId.getBytes(StandardCharsets.UTF_8))));
  }

  /** O id da sessão que a referência falsa carrega; vazio se a imagem não for uma delas. */
  static Optional<String> sessaoDaReferencia(Imagem referencia) {
    byte[] conteudo = referencia.bytes();
    int tamanho = PREFIXO_DA_REFERENCIA.length;
    if (conteudo.length <= tamanho
        || !Arrays.equals(PREFIXO_DA_REFERENCIA, Arrays.copyOf(conteudo, tamanho))) {
      return Optional.empty();
    }
    return Optional.of(
        new String(conteudo, tamanho, conteudo.length - tamanho, StandardCharsets.UTF_8));
  }

  /**
   * A conta dona da sessão; vazio se a sessão ou a conta não existirem ou se ela não tiver e-mail.
   */
  static Optional<Conta> contaDaSessao(
      RepositorioDeVerificacoes verificacoes, RepositorioDeContas contas, String sessaoId) {
    return verificacoes
        .porSessao(sessaoId)
        .flatMap(tentativa -> contas.porId(tentativa.conta()))
        .filter(conta -> conta.email() != null);
  }

  /** Os marcadores da parte local do e-mail, depois do primeiro {@code +}; vazio sem e-mail. */
  static Set<String> marcadores(Conta conta) {
    Email email = conta.email();
    if (email == null) {
      return Set.of();
    }
    String local = email.valor().substring(0, email.valor().indexOf('@'));
    return Arrays.stream(local.split("\\+")).skip(1).collect(Collectors.toUnmodifiableSet());
  }

  private static FaixaEstimada faixa(Set<String> marcadores) {
    if (marcadores.contains("idade15")) {
      return FAIXA_DE_MENOR;
    }
    if (marcadores.contains("idade19")) {
      return FAIXA_DE_DUVIDA;
    }
    return FAIXA_PADRAO;
  }

  private static byte[] concatenar(byte[] primeiro, byte[] segundo) {
    byte[] junto = Arrays.copyOf(primeiro, primeiro.length + segundo.length);
    System.arraycopy(segundo, 0, junto, primeiro.length, segundo.length);
    return junto;
  }
}
