package bipotheking.net.duora.retencao;

/** O que uma política fez numa execução: quantos itens apagou, ou se falhou (com zero). */
public record ResultadoDaExpiracao(String politica, int removidos, boolean falhou) {}
