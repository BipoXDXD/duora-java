package bipotheking.net.duora.sala;

import bipotheking.net.duora.conta.ContaId;

/** Um encontro com as duas pessoas definidas: pareado pela fila ou privado já aceito. */
public record EncontroAberto(String encontroId, String cenarioId, ContaId a, ContaId b) {}
