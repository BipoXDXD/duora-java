package bipotheking.net.duora.perfil;

import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;

/**
 * Resposta do envio: o estado em que a foto entrou ({@code APROVADA} se a moderação automática a
 * liberou, senão {@code PENDENTE}) e a URL da nítida. É também a resposta que a idempotência guarda
 * e devolve igual numa repetição.
 */
public record FotoEnviada(EstadoDaFoto estado, String url) {}
