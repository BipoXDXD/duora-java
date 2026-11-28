package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.CodigoSessao;
import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.perfil.TriagemDeFoto.FotoTriada;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import bipotheking.net.duora.web.ArmazemDeImagens;
import bipotheking.net.duora.web.CodigoWeb;
import bipotheking.net.duora.web.ImagemProcessada;
import bipotheking.net.duora.web.Variante;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A gravação de uma foto enviada, depois da triagem: a nova entra no lugar da pendente e da marca
 * de recusa anteriores e, se a moderação automática a liberou, já entra aprovada, no lugar da
 * aprovada anterior. A linha e os arquivos andam juntos: os arquivos novos só ficam se a transação
 * confirmar, e os das fotos trocadas só saem depois disso.
 */
@Component
class EnvioDeFoto {

  private static final Logger LOG = LoggerFactory.getLogger(EnvioDeFoto.class);
  private static final String MENSAGEM_INDISPONIVEL =
      "Não conseguimos processar a foto agora. Tente de novo em alguns minutos.";

  private final RepositorioDeFotos fotos;
  private final ConsultaDeContas contas;
  private final TriagemDeFoto triagem;
  private final ArmazemDeImagens armazem;
  private final ArquivosDasFotos arquivos;
  private final GeradorDeIds geradorDeIds;
  private final TransactionTemplate transacao;
  private final TransactionTemplate transacaoAParte;
  private final Clock relogio;

  EnvioDeFoto(
      RepositorioDeFotos fotos,
      ConsultaDeContas contas,
      TriagemDeFoto triagem,
      ArmazemDeImagens armazem,
      GeradorDeIds geradorDeIds,
      TransactionTemplate transacao,
      Clock relogio) {
    this.fotos = fotos;
    this.contas = contas;
    this.triagem = triagem;
    this.armazem = armazem;
    this.arquivos = new ArquivosDasFotos(armazem);
    this.geradorDeIds = geradorDeIds;
    this.transacao = transacao;
    this.transacaoAParte =
        new TransactionTemplate(Objects.requireNonNull(transacao.getTransactionManager()));
    this.transacaoAParte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.transacaoAParte.setReadOnly(true);
    this.relogio = relogio;
  }

  /**
   * Tria e grava a foto. A triagem roda fora da transação; {@code aoGravar} roda dentro dela, com a
   * resposta, depois de tudo gravado (a idempotência conclui a reserva ali).
   *
   * @throws ErroDeNegocio os do {@link TriagemDeFoto}; {@code PROCESSAMENTO_INDISPONIVEL} se o
   *     armazém falhar (disco cheio, por exemplo); {@code CONTA_INDISPONIVEL} se a conta deixou de
   *     estar ativa
   */
  FotoEnviada efetuar(ContaId conta, byte[] arquivo, Consumer<FotoEnviada> aoGravar) {
    try (FotoTriada triada = triagem.triar(arquivo)) {
      ImagemProcessada imagem = triada.imagem();
      FotoDePerfil nova =
          FotoDePerfil.pendente(
              new FotoId(geradorDeIds.novo()),
              conta,
              imagem.largura(),
              imagem.altura(),
              relogio.instant());
      EstadoDaFoto estado = triada.liberada() ? EstadoDaFoto.APROVADA : EstadoDaFoto.PENDENTE;
      FotoEnviada resposta = new FotoEnviada(estado, UrlDaFoto.de(nova.id(), Variante.NITIDA));
      try {
        transacao.executeWithoutResult(_ -> {
          gravar(nova, imagem, triada.liberada());
          aoGravar.accept(resposta);
        });
      } catch (UncheckedIOException erro) {
        LOG.error("O armazém de imagens não guardou a foto enviada", erro);
        throw new ErroDeNegocio(CodigoWeb.PROCESSAMENTO_INDISPONIVEL, MENSAGEM_INDISPONIVEL);
      }
      return resposta;
    }
  }

  /**
   * A trava da conta vem antes de tudo: serializa envios simultâneos (a unicidade da pendente e da
   * aprovada) e o envio com a exclusão da conta, que toma a mesma trava. Por isso a conta é relida
   * depois dela: se a exclusão (ou a moderação) passou antes, nada é gravado.
   */
  private void gravar(FotoDePerfil nova, ImagemProcessada imagem, boolean liberada) {
    ContaId conta = nova.conta();
    fotos.travarConta(conta);
    exigirAtiva(conta);
    List<FotoId> substituidas = new ArrayList<>(3);
    fotos.apagarDoEstado(conta, EstadoDaFoto.PENDENTE).ifPresent(substituidas::add);
    fotos.apagarDoEstado(conta, EstadoDaFoto.RECUSADA).ifPresent(substituidas::add);
    fotos.inserirPendente(nova);
    if (liberada) {
      fotos.apagarDoEstado(conta, EstadoDaFoto.APROVADA).ifPresent(substituidas::add);
      if (!fotos.decidir(nova.id(), EstadoDaFoto.APROVADA, relogio.instant())) {
        throw new IllegalStateException("A foto recém-gravada deixou de estar pendente");
      }
    }
    TransactionSynchronizationManager.registerSynchronization(
        new ArquivosAoTerminar(nova.id(), List.copyOf(substituidas)));
    armazem.guardar(FotosDoPerfil.CATEGORIA, nova.id().valor(), imagem);
  }

  private void exigirAtiva(ContaId conta) {
    if (!contas.porId(conta).map(ContaResumo::ativa).orElse(false)) {
      throw new ErroDeNegocio(
          CodigoSessao.CONTA_INDISPONIVEL, CodigoSessao.CONTA_INDISPONIVEL.mensagem());
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
        case STATUS_COMMITTED -> arquivos.apagar(substituidas);
        case STATUS_ROLLED_BACK -> arquivos.apagar(List.of(nova));
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
      arquivos.apagar(confirmada ? substituidas : List.of(nova));
    }
  }
}
