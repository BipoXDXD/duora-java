package bipotheking.net.duora.room;

import bipotheking.net.duora.game.Assento;
import java.util.Objects;

/**
 * Quem ocupa um assento. Do token de acesso o servidor guarda só o resumo SHA-256.
 *
 * @param resumoToken SHA-256 em hexadecimal do token do cookie
 */
record Participante(Assento assento, Apelido apelido, String resumoToken) {

  Participante {
    Objects.requireNonNull(assento);
    Objects.requireNonNull(apelido);
    Objects.requireNonNull(resumoToken);
  }
}
