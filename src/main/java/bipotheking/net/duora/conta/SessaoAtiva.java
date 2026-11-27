package bipotheking.net.duora.conta;

/**
 * Sessão válida e o estado atual da conta dona dela. {@code renovada} diz se esta validação gravou
 * a renovação deslizante.
 */
public record SessaoAtiva(ContaId conta, EstadoConta estado, boolean renovada) {}
