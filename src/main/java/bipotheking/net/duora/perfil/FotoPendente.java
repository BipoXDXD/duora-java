package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;

/** Uma foto à espera da moderação. */
public record FotoPendente(FotoId id, ContaId conta, Instant enviadaEm) {}
