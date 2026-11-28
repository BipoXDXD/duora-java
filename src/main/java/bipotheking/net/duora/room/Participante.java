package bipotheking.net.duora.room;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.game.Assento;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Quem ocupa um assento. Do token de acesso o servidor guarda só o resumo SHA-256.
 *
 * @param resumoToken SHA-256 em hexadecimal do token do cookie
 * @param conta a conta dona do assento numa sala pareada; nula no terraço anônimo
 */
record Participante(Assento assento, Apelido apelido, String resumoToken, @Nullable ContaId conta) {

  Participante {
    Objects.requireNonNull(assento);
    Objects.requireNonNull(apelido);
    Objects.requireNonNull(resumoToken);
  }

  /** Assento do terraço anônimo, sem conta. */
  static Participante anonimo(Assento assento, Apelido apelido, String resumoToken) {
    return new Participante(assento, apelido, resumoToken, null);
  }
}
