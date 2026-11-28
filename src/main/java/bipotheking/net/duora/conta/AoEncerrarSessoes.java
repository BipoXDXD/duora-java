package bipotheking.net.duora.conta;

/**
 * Gancho de outro módulo no logout e na revogação das sessões de uma conta: o que segue aberto em
 * nome dela (como os streams de tempo real) é fechado. Roda depois de apagar a sessão, na thread de
 * quem encerrou; não lança.
 */
public interface AoEncerrarSessoes {

  void encerradas(ContaId conta);
}
