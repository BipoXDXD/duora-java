package bipotheking.net.duora.perfil;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import bipotheking.net.duora.web.ArmazemDeImagens;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A moderação das fotos de perfil, sem HTTP: a fila das pendentes, a aprovação e a recusa. Cada
 * decisão toma a trava das fotos da conta, a mesma do envio e da exclusão; os arquivos que deixam
 * de valer só saem depois do commit.
 */
@Service
public class FilaDeFotos {

  /** Maior página da fila. */
  static final int LIMITE_MAXIMO = 50;

  private final RepositorioDeFotos fotos;
  private final ArquivosDasFotos arquivos;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  FilaDeFotos(
      RepositorioDeFotos fotos,
      ArmazemDeImagens armazem,
      TransactionTemplate transacao,
      Clock relogio) {
    this.fotos = fotos;
    this.arquivos = new ArquivosDasFotos(armazem);
    this.transacao = transacao;
    this.relogio = relogio;
  }

  /**
   * Até {@code limite} pendentes, das mais antigas para as mais novas.
   *
   * @throws IllegalArgumentException se {@code limite} estiver fora de 1 a 50
   */
  public List<FotoPendente> pendentes(int limite) {
    if (limite < 1 || limite > LIMITE_MAXIMO) {
      throw new IllegalArgumentException("limite fora de 1 a " + LIMITE_MAXIMO + ": " + limite);
    }
    return fotos.pendentes(limite);
  }

  /**
   * Aprova a pendente: ela passa a ser a aprovada da conta, no lugar da anterior, e a marca de
   * recusa some. Os arquivos da anterior saem depois do commit.
   *
   * @throws ErroDeNegocio {@code FOTO_INEXISTENTE} se a foto não existe ou já não está pendente
   */
  public void aprovar(FotoId id) {
    transacao.executeWithoutResult(_ -> {
      FotoDePerfil foto = pendenteTravada(id);
      List<FotoId> semValor = new ArrayList<>(2);
      fotos.apagarDoEstado(foto.conta(), EstadoDaFoto.APROVADA).ifPresent(semValor::add);
      decidir(id, EstadoDaFoto.APROVADA);
      fotos.apagarDoEstado(foto.conta(), EstadoDaFoto.RECUSADA).ifPresent(semValor::add);
      arquivos.apagarDepoisDoCommit(semValor);
    });
  }

  /**
   * Recusa a pendente: a linha fica só como a marca da recusa (com {@code decidida_em} = agora), no
   * lugar da recusa anterior. Os arquivos dela saem depois do commit.
   *
   * @throws ErroDeNegocio {@code FOTO_INEXISTENTE} se a foto não existe ou já não está pendente
   */
  public void recusar(FotoId id) {
    transacao.executeWithoutResult(_ -> {
      FotoDePerfil foto = pendenteTravada(id);
      List<FotoId> semValor = new ArrayList<>(2);
      fotos.apagarDoEstado(foto.conta(), EstadoDaFoto.RECUSADA).ifPresent(semValor::add);
      decidir(id, EstadoDaFoto.RECUSADA);
      semValor.add(id);
      arquivos.apagarDepoisDoCommit(semValor);
    });
  }

  /**
   * A foto pendente, com a trava da conta dela tomada. A leitura vem antes da trava (é ela que diz
   * a conta); quem decidiu no meio tempo é pego pelo {@link #decidir} condicional.
   */
  private FotoDePerfil pendenteTravada(FotoId id) {
    FotoDePerfil foto =
        fotos
            .porId(id)
            .filter(achada -> achada.estado() == EstadoDaFoto.PENDENTE)
            .orElseThrow(CodigoFoto.FOTO_INEXISTENTE::erro);
    fotos.travarConta(foto.conta());
    return foto;
  }

  private void decidir(FotoId id, EstadoDaFoto estado) {
    if (!fotos.decidir(id, estado, relogio.instant())) {
      throw CodigoFoto.FOTO_INEXISTENTE.erro();
    }
  }
}
