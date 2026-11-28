package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.AoExcluirConta;
import bipotheking.net.duora.conta.ContaId;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Apaga as fotos na exclusão da conta: as linhas na transação da exclusão, os arquivos depois do
 * commit dela. A trava das fotos da conta, a mesma do envio, fecha a corrida: o envio que chega
 * durante a exclusão espera o commit e encontra a conta excluída; o que já estava em curso termina
 * antes, e a foto dele sai junto. A conta fica como lápide (a linha não sai), então o {@code on
 * delete cascade} da chave estrangeira nunca dispara sozinho.
 *
 * <p>Roda antes dos outros ganchos: a trava das fotos vem antes de qualquer linha da conta, na
 * mesma ordem do envio (trava consultiva, linhas de {@code foto}, linha de {@code conta}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class FotoAoExcluirConta implements AoExcluirConta {

  private final FotosDoPerfil fotos;

  FotoAoExcluirConta(FotosDoPerfil fotos) {
    this.fotos = fotos;
  }

  @Override
  public void excluir(ContaId conta) {
    fotos.removerTodas(conta);
  }
}
