package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.dominio.DecisaoDeIdade;
import bipotheking.net.duora.conta.dominio.FaixaEstimada;
import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ReservaDeTentativa.Feita;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import bipotheking.net.duora.conta.dominio.SituacaoDaProva;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A prova de vida e a conferência de documento falsas, guiadas pelo marcador do e-mail. */
class FalsosDeVerificacaoTest extends BancoDeTeste {

  private static final LocalDate DECLARADA = LocalDate.of(1990, 5, 10);
  private static final Imagem FRENTE = jpeg(0x01);
  private static final Imagem VERSO = jpeg(0x02);

  @Autowired private ProvaDeVidaFalsa prova;
  @Autowired private ConferenciaDeDocumentoFalsa conferencia;
  @Autowired private RepositorioDeVerificacoes verificacoes;
  @Autowired private ContasDeTeste contas;

  @Test
  void padraoAprova() {
    ResultadoDaProva resultado = prova.resultado(sessaoSemMarcador());

    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.CONCLUIDA);
    assertThat(resultado.confianca()).isEqualTo(99.0);
    assertThat(resultado.faixa()).contains(new FaixaEstimada(30, 40));
    assertThat(resultado.referencia()).isPresent();
    assertThat(DecisaoDeIdade.pelaSelfie(resultado)).isEqualTo(ResultadoDaVerificacao.APROVADA);
  }

  @Test
  void idade15Recusa() {
    ResultadoDaProva resultado = prova.resultado(sessaoCom("idade15"));

    assertThat(resultado.faixa()).contains(new FaixaEstimada(12, 17));
    assertThat(DecisaoDeIdade.pelaSelfie(resultado)).isEqualTo(ResultadoDaVerificacao.RECUSADA);
  }

  @Test
  void idade19CaiNaDuvida() {
    ResultadoDaProva resultado = prova.resultado(sessaoCom("idade19"));

    assertThat(resultado.faixa()).contains(new FaixaEstimada(17, 24));
    assertThat(DecisaoDeIdade.pelaSelfie(resultado)).isEqualTo(ResultadoDaVerificacao.DUVIDA);
  }

  @Test
  void vidafalhaFalha() {
    ResultadoDaProva resultado = prova.resultado(sessaoCom("vidafalha"));

    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.FALHOU);
    assertThat(DecisaoDeIdade.pelaSelfie(resultado)).isEqualTo(ResultadoDaVerificacao.REPETIR);
  }

  @Test
  void sessaoDesconhecidaExpira() {
    ResultadoDaProva resultado = prova.resultado("falsa-" + UUID.randomUUID());

    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.EXPIRADA);
    assertThat(resultado.faixa()).isEmpty();
    assertThat(resultado.referencia()).isEmpty();
  }

  @Test
  void credencialVence15MinDepois() {
    assertThat(prova.emitir(UUID.randomUUID()).expiraEm())
        .isEqualTo(Instant.parse("2026-11-22T12:15:00Z"));
  }

  @Test
  void modoEhFalso() {
    assertThat(prova.modo()).isEqualTo(ModoDeCaptura.FALSO);
  }

  @Test
  void documentoPadraoDevolveADeclarada() {
    assertThat(conferir(sessaoSemMarcador()))
        .isEqualTo(new ResultadoDoDocumento(true, Optional.of(DECLARADA), true));
  }

  @Test
  void docmenorTem15Anos() {
    assertThat(conferir(sessaoCom("docmenor")))
        .isEqualTo(new ResultadoDoDocumento(true, Optional.of(LocalDate.of(2011, 11, 22)), true));
  }

  @Test
  void docoutroNaoConfereORosto() {
    assertThat(conferir(sessaoCom("docoutro")))
        .isEqualTo(new ResultadoDoDocumento(true, Optional.of(DECLARADA), false));
  }

  @Test
  void docilegivelNaoAceita() {
    assertThat(conferir(sessaoCom("docilegivel")))
        .isEqualTo(new ResultadoDoDocumento(false, Optional.empty(), false));
  }

  @Test
  void selfieDeVerdadeNaoAceita() {
    assertThat(conferencia.conferir(FRENTE, VERSO, jpeg(0xE1)))
        .isEqualTo(new ResultadoDoDocumento(false, Optional.empty(), false));
  }

  @Test
  void marcadoresSeJuntamComMais() {
    String sessao = sessaoCom("idade19+docmenor");

    assertThat(prova.resultado(sessao).faixa()).contains(new FaixaEstimada(17, 24));
    assertThat(conferir(sessao).dataDeNascimento()).contains(LocalDate.of(2011, 11, 22));
  }

  private ResultadoDoDocumento conferir(String sessao) {
    Imagem referencia = prova.resultado(sessao).referencia().orElseThrow();
    return conferencia.conferir(FRENTE, VERSO, referencia);
  }

  private String sessaoSemMarcador() {
    return sessaoDa(contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, DECLARADA).id());
  }

  private String sessaoCom(String marcador) {
    return sessaoDa(contas.criarAguardando(marcador, DECLARADA).id());
  }

  private String sessaoDa(ContaId conta) {
    UUID tentativa =
        ((Feita) verificacoes.reservar(conta, FinalidadeDaTentativa.SELFIE, 3, INICIO)).tentativa();
    String sessao = prova.criarSessao(tentativa).id();
    verificacoes.associarSessao(tentativa, sessao);
    return sessao;
  }

  private static Imagem jpeg(int quartoByte) {
    return Imagem.ler(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) quartoByte})
        .orElseThrow();
  }
}
