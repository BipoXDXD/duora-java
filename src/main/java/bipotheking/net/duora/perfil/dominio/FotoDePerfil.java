package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.FotoId;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Uma foto de perfil. Largura e altura são as da nítida (maior lado até 1600). Só a pendente fica
 * sem {@code decididaEm}.
 */
public record FotoDePerfil(
    FotoId id,
    ContaId conta,
    EstadoDaFoto estado,
    int largura,
    int altura,
    Instant enviadaEm,
    @Nullable Instant decididaEm) {

  public static final int LADO_MAXIMO = 1600;

  public FotoDePerfil {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(conta, "conta");
    Objects.requireNonNull(estado, "estado");
    Objects.requireNonNull(enviadaEm, "enviadaEm");
    if (largura < 1 || largura > LADO_MAXIMO || altura < 1 || altura > LADO_MAXIMO) {
      throw new IllegalArgumentException("Dimensões fora de 1.." + LADO_MAXIMO);
    }
    if ((estado == EstadoDaFoto.PENDENTE) != (decididaEm == null)) {
      throw new IllegalArgumentException("Só a foto pendente fica sem decisão");
    }
  }

  /** A foto recém-enviada, à espera da moderação. */
  public static FotoDePerfil pendente(
      FotoId id, ContaId conta, int largura, int altura, Instant enviadaEm) {
    return new FotoDePerfil(id, conta, EstadoDaFoto.PENDENTE, largura, altura, enviadaEm, null);
  }
}
