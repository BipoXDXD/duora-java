package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;

/** Decide quanto {@code quemVe} enxerga das fotos de {@code titular}. */
public interface AcessoAFoto {

  NivelDeAcesso nivel(ContaId quemVe, ContaId titular);
}
