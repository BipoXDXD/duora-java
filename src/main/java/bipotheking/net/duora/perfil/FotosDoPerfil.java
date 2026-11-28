package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.Resumos;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import bipotheking.net.duora.web.ArmazemDeImagens;
import bipotheking.net.duora.web.Idempotencia;
import bipotheking.net.duora.web.Idempotencia.Reserva;
import bipotheking.net.duora.web.LimiteDeTaxa;
import bipotheking.net.duora.web.ProcessadorDeImagem;
import bipotheking.net.duora.web.Variante;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * As fotos de perfil: o envio (com o limite de taxa e a idempotência; a gravação fica no {@link
 * EnvioDeFoto}), o que a própria conta vê delas, o que outras contas podem abrir e a remoção.
 */
@Service
public class FotosDoPerfil {

  /** Categoria das fotos de perfil no {@link ArmazemDeImagens}. */
  static final String CATEGORIA = "perfil";

  private static final String LIMITE_POR_CONTA = "foto-conta";
  private static final String ESCOPO_DE_IDEMPOTENCIA = "foto:";

  private final RepositorioDeFotos fotos;
  private final ArmazemDeImagens armazem;
  private final ArquivosDasFotos arquivos;
  private final TransactionTemplate transacao;
  private final AcessoAFoto acesso;
  private final LimiteDeTaxa limiteDeTaxa;
  private final Idempotencia idempotencia;
  private final EnvioDeFoto envio;

  FotosDoPerfil(
      RepositorioDeFotos fotos,
      ArmazemDeImagens armazem,
      TransactionTemplate transacao,
      AcessoAFoto acesso,
      LimiteDeTaxa limiteDeTaxa,
      Idempotencia idempotencia,
      EnvioDeFoto envio) {
    this.fotos = fotos;
    this.armazem = armazem;
    this.arquivos = new ArquivosDasFotos(armazem);
    this.transacao = transacao;
    this.acesso = acesso;
    this.limiteDeTaxa = limiteDeTaxa;
    this.idempotencia = idempotencia;
    this.envio = envio;
  }

  /**
   * Processa o arquivo, passa a nítida pela moderação automática e o grava no lugar da pendente
   * anterior, apagando a marca de recusa: liberada pela moderação, a foto já entra aprovada (no
   * lugar da aprovada anterior); sinalizada ou sem moderação, entra pendente. Com chave, a
   * repetição com o mesmo arquivo devolve a mesma resposta sem efeito; qualquer falha depois da
   * reserva libera a chave.
   *
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}; os da {@link Idempotencia#reservar}; os do {@link
   *     ProcessadorDeImagem}; {@code PROCESSAMENTO_INDISPONIVEL} se o armazém falhar (disco cheio,
   *     por exemplo); {@code CONTA_INDISPONIVEL} se a conta deixou de estar ativa
   */
  public FotoEnviada enviar(ContaId conta, byte[] arquivo, @Nullable String chaveDeIdempotencia) {
    limiteDeTaxa.consumir(LIMITE_POR_CONTA, conta.toString());
    if (chaveDeIdempotencia == null) {
      return envio.efetuar(conta, arquivo, _ -> {});
    }
    String escopo = ESCOPO_DE_IDEMPOTENCIA + conta;
    Reserva<FotoEnviada> reserva =
        idempotencia.reservar(
            escopo, chaveDeIdempotencia, Resumos.sha256Hex(arquivo), FotoEnviada.class);
    return switch (reserva) {
      case Reserva.JaRespondida<FotoEnviada>(FotoEnviada resposta) -> resposta;
      case Reserva.Tua<FotoEnviada>(UUID dono) ->
          efetuarComChave(conta, arquivo, new ChaveReservada(escopo, chaveDeIdempotencia, dono));
    };
  }

  /** As fotos que a própria conta vê: aprovada, pendente e o instante da última recusa. */
  public FotoDoTitular doTitular(ContaId conta) {
    @Nullable UrlsDaFoto aprovada = null;
    @Nullable UrlsDaFoto pendente = null;
    @Nullable Instant recusadaEm = null;
    for (FotoDePerfil foto : fotos.daConta(conta)) {
      switch (foto.estado()) {
        case APROVADA -> aprovada = UrlsDaFoto.de(foto.id());
        case PENDENTE -> pendente = UrlsDaFoto.de(foto.id());
        case RECUSADA -> recusadaEm = foto.decididaEm();
      }
    }
    return new FotoDoTitular(aprovada, pendente, recusadaEm);
  }

  /**
   * Apaga a aprovada, a pendente e a marca de recusa da conta, na transação do chamador (ou numa
   * nova, se não houver). Toma a trava das fotos da conta antes de tudo, como o envio: um envio em
   * curso termina antes, e o que ele gravou também sai. Os arquivos saem depois do commit; o que
   * falhar fica para a retenção de fotos órfãs.
   */
  public void removerTodas(ContaId conta) {
    transacao.executeWithoutResult(_ -> {
      fotos.travarConta(conta);
      arquivos.apagarDepoisDoCommit(fotos.apagarDaConta(conta));
    });
  }

  /** A URL da variante da foto aprovada do titular, se houver. */
  public Optional<String> urlDaAprovada(ContaId titular, Variante variante) {
    return fotos.daConta(titular).stream()
        .filter(foto -> foto.estado() == EstadoDaFoto.APROVADA)
        .findFirst()
        .map(foto -> UrlDaFoto.de(foto.id(), variante));
  }

  /**
   * Os bytes da variante, se {@code quemVe} puder vê-la. Vazio é a mesma resposta para a foto que
   * não existe, a recusada, a pendente de outra conta e a variante que o nível não alcança: quem
   * pede não distingue um caso do outro.
   */
  public Optional<Resource> abrir(ContaId quemVe, FotoId id, Variante variante) {
    return fotos
        .porId(id)
        .filter(foto -> podeVer(quemVe, foto, variante))
        .flatMap(foto -> armazem.ler(CATEGORIA, foto.id().valor(), variante));
  }

  /** A pendente só a titular vê; a aprovada, quem tem o nível que a variante pede. */
  private boolean podeVer(ContaId quemVe, FotoDePerfil foto, Variante variante) {
    boolean visivel =
        switch (foto.estado()) {
          case APROVADA -> true;
          case PENDENTE -> quemVe.equals(foto.conta());
          case RECUSADA -> false;
        };
    return visivel && acesso.nivel(quemVe, foto.conta()).permite(variante);
  }

  private FotoEnviada efetuarComChave(ContaId conta, byte[] arquivo, ChaveReservada chave) {
    try {
      return envio.efetuar(
          conta,
          arquivo,
          resposta -> idempotencia.concluir(chave.escopo(), chave.chave(), chave.dono(), resposta));
    } catch (RuntimeException erro) {
      try {
        idempotencia.liberar(chave.escopo(), chave.chave(), chave.dono());
      } catch (RuntimeException naLiberacao) {
        erro.addSuppressed(naLiberacao);
      }
      throw erro;
    }
  }

  /** O escopo, a chave de idempotência e o dono da reserva, que conclui ou libera. */
  private record ChaveReservada(String escopo, String chave, UUID dono) {}
}
