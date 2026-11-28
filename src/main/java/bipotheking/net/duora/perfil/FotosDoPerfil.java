package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.CodigoSessao;
import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.nucleo.Resumos;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import bipotheking.net.duora.web.ArmazemDeImagens;
import bipotheking.net.duora.web.CodigoWeb;
import bipotheking.net.duora.web.Idempotencia;
import bipotheking.net.duora.web.Idempotencia.Reserva;
import bipotheking.net.duora.web.ImagemProcessada;
import bipotheking.net.duora.web.LimiteDeTaxa;
import bipotheking.net.duora.web.ProcessadorDeImagem;
import bipotheking.net.duora.web.Variante;
import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * As fotos de perfil: o envio (toda foto nova entra pendente, no lugar da pendente anterior) e o
 * que a própria conta vê delas. A linha e os arquivos andam juntos: os arquivos novos só ficam se a
 * transação confirmar, e os da pendente trocada só saem depois disso.
 */
@Service
public class FotosDoPerfil {

  /** Categoria das fotos de perfil no {@link ArmazemDeImagens}. */
  static final String CATEGORIA = "perfil";

  private static final Logger LOG = LoggerFactory.getLogger(FotosDoPerfil.class);
  private static final String LIMITE_POR_CONTA = "foto-conta";
  private static final String ESCOPO_DE_IDEMPOTENCIA = "foto:";
  private static final String MENSAGEM_INDISPONIVEL =
      "Não conseguimos processar a foto agora. Tente de novo em alguns minutos.";

  private final RepositorioDeFotos fotos;
  private final ConsultaDeContas contas;
  private final ProcessadorDeImagem processador;
  private final ArmazemDeImagens armazem;
  private final LimiteDeTaxa limiteDeTaxa;
  private final Idempotencia idempotencia;
  private final GeradorDeIds geradorDeIds;
  private final TransactionTemplate transacao;
  private final TransactionTemplate transacaoAParte;
  private final Clock relogio;
  private final AcessoAFoto acesso;

  FotosDoPerfil(
      RepositorioDeFotos fotos,
      ConsultaDeContas contas,
      ProcessadorDeImagem processador,
      ArmazemDeImagens armazem,
      LimiteDeTaxa limiteDeTaxa,
      Idempotencia idempotencia,
      GeradorDeIds geradorDeIds,
      TransactionTemplate transacao,
      Clock relogio,
      AcessoAFoto acesso) {
    this.fotos = fotos;
    this.contas = contas;
    this.processador = processador;
    this.armazem = armazem;
    this.limiteDeTaxa = limiteDeTaxa;
    this.idempotencia = idempotencia;
    this.geradorDeIds = geradorDeIds;
    this.transacao = transacao;
    this.transacaoAParte =
        new TransactionTemplate(Objects.requireNonNull(transacao.getTransactionManager()));
    this.transacaoAParte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.transacaoAParte.setReadOnly(true);
    this.relogio = relogio;
    this.acesso = acesso;
  }

  /**
   * Processa o arquivo e o grava como a foto pendente da conta, trocando a pendente anterior e
   * apagando a marca de recusa. Com chave, a repetição com o mesmo arquivo devolve a mesma resposta
   * sem efeito; qualquer falha depois da reserva libera a chave.
   *
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}; os da {@link Idempotencia#reservar}; os do {@link
   *     ProcessadorDeImagem}; {@code PROCESSAMENTO_INDISPONIVEL} se o armazém falhar (disco cheio,
   *     por exemplo); {@code CONTA_INDISPONIVEL} se a conta deixou de estar ativa
   */
  public FotoEnviada enviar(ContaId conta, byte[] arquivo, @Nullable String chaveDeIdempotencia) {
    limiteDeTaxa.consumir(LIMITE_POR_CONTA, conta.toString());
    if (chaveDeIdempotencia == null) {
      return efetuar(conta, arquivo, null);
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
      return efetuar(conta, arquivo, chave);
    } catch (RuntimeException erro) {
      try {
        idempotencia.liberar(chave.escopo(), chave.chave(), chave.dono());
      } catch (RuntimeException naLiberacao) {
        erro.addSuppressed(naLiberacao);
      }
      throw erro;
    }
  }

  /** O processamento roda fora da transação: pode levar segundos. */
  private FotoEnviada efetuar(ContaId conta, byte[] arquivo, @Nullable ChaveReservada chave) {
    try (ImagemProcessada imagem = processador.processar(new ByteArrayInputStream(arquivo))) {
      FotoDePerfil nova =
          FotoDePerfil.pendente(
              new FotoId(geradorDeIds.novo()),
              conta,
              imagem.largura(),
              imagem.altura(),
              relogio.instant());
      FotoEnviada resposta =
          new FotoEnviada(EstadoDaFoto.PENDENTE.name(), UrlDaFoto.de(nova.id(), Variante.NITIDA));
      try {
        transacao.executeWithoutResult(_ -> gravar(nova, imagem, resposta, chave));
      } catch (UncheckedIOException erro) {
        LOG.error("O armazém de imagens não guardou a foto enviada", erro);
        throw new ErroDeNegocio(CodigoWeb.PROCESSAMENTO_INDISPONIVEL, MENSAGEM_INDISPONIVEL);
      }
      return resposta;
    }
  }

  /**
   * A trava da conta vem antes de tudo: serializa envios simultâneos (a unicidade da pendente) e o
   * envio com a exclusão da conta, que toma a mesma trava. Por isso a conta é relida depois dela:
   * se a exclusão (ou a moderação) passou antes, nada é gravado.
   */
  private void gravar(
      FotoDePerfil nova,
      ImagemProcessada imagem,
      FotoEnviada resposta,
      @Nullable ChaveReservada chave) {
    ContaId conta = nova.conta();
    fotos.travarConta(conta);
    exigirAtiva(conta);
    List<FotoId> substituidas = new ArrayList<>(2);
    fotos.apagarDoEstado(conta, EstadoDaFoto.PENDENTE).ifPresent(substituidas::add);
    fotos.apagarDoEstado(conta, EstadoDaFoto.RECUSADA).ifPresent(substituidas::add);
    fotos.inserirPendente(nova);
    TransactionSynchronizationManager.registerSynchronization(
        new ArquivosAoTerminar(nova.id(), List.copyOf(substituidas)));
    armazem.guardar(CATEGORIA, nova.id().valor(), imagem);
    if (chave != null) {
      idempotencia.concluir(chave.escopo(), chave.chave(), chave.dono(), resposta);
    }
  }

  private void exigirAtiva(ContaId conta) {
    boolean ativa =
        contas.porId(conta).map(resumo -> resumo.estado() == EstadoConta.ATIVA).orElse(false);
    if (!ativa) {
      throw new ErroDeNegocio(
          CodigoSessao.CONTA_INDISPONIVEL, CodigoSessao.CONTA_INDISPONIVEL.mensagem());
    }
  }

  private void apagarArquivos(List<FotoId> ids) {
    for (FotoId id : ids) {
      try {
        armazem.apagar(CATEGORIA, id.valor());
      } catch (RuntimeException erro) {
        // A retenção de fotos órfãs varre o que sobrar.
        LOG.warn("Não foi possível apagar os arquivos de uma foto", erro);
      }
    }
  }

  /**
   * Confirmada a transação, apaga os arquivos das fotos substituídas; desfeita, os da nova.
   * Registrada antes do {@code guardar}, cobre também a falha no meio dele.
   *
   * <p>Quando o próprio {@code COMMIT} falha (uma constraint adiada, a conexão que cai), o Spring
   * avisa desfecho desconhecido: a linha nova decide, lida numa transação à parte. Se nem essa
   * leitura der certo, nada é apagado, e a retenção de fotos órfãs varre o que sobrar.
   */
  private final class ArquivosAoTerminar implements TransactionSynchronization {

    private final FotoId nova;
    private final List<FotoId> substituidas;

    ArquivosAoTerminar(FotoId nova, List<FotoId> substituidas) {
      this.nova = nova;
      this.substituidas = substituidas;
    }

    @Override
    public void afterCompletion(int status) {
      switch (status) {
        case STATUS_COMMITTED -> apagarArquivos(substituidas);
        case STATUS_ROLLED_BACK -> apagarArquivos(List.of(nova));
        default -> conferirDesfecho();
      }
    }

    private void conferirDesfecho() {
      boolean confirmada;
      try {
        confirmada =
            Boolean.TRUE.equals(
                transacaoAParte.execute(_ -> !fotos.existentes(List.of(nova.valor())).isEmpty()));
      } catch (RuntimeException erro) {
        LOG.warn("Desfecho desconhecido do envio de uma foto", erro);
        return;
      }
      apagarArquivos(confirmada ? substituidas : List.of(nova));
    }
  }

  /** O escopo, a chave de idempotência e o dono da reserva, que conclui ou libera. */
  private record ChaveReservada(String escopo, String chave, UUID dono) {}
}
