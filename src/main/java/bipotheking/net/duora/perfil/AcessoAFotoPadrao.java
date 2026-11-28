package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EstadoConta;
import org.springframework.stereotype.Component;

/**
 * A regra desta etapa: titular que não está ativa some para todos; ativa, ela vê a própria foto
 * nítida e as outras contas veem a borrada. As etapas seguintes refinam o caso das outras contas
 * (conexão, bloqueio, denúncia).
 */
@Component
public class AcessoAFotoPadrao implements AcessoAFoto {

  private final ConsultaDeContas contas;

  public AcessoAFotoPadrao(ConsultaDeContas contas) {
    this.contas = contas;
  }

  @Override
  public NivelDeAcesso nivel(ContaId quemVe, ContaId titular) {
    boolean ativa =
        contas.porId(titular).map(resumo -> resumo.estado() == EstadoConta.ATIVA).orElse(false);
    if (!ativa) {
      return NivelDeAcesso.NENHUM;
    }
    return quemVe.equals(titular) ? NivelDeAcesso.NITIDA : NivelDeAcesso.BORRADA;
  }
}
