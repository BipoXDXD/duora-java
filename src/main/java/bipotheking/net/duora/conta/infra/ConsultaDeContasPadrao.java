package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * A consulta é o repositório visto de fora: a conta inteira reduzida ao {@link ContaResumo}. Fica
 * fora do {@code ContasJdbc} porque as duas portas têm {@code porId(ContaId)} com retornos
 * diferentes, e uma classe Java não implementa as duas. O selo {@code verificada} vem da aprovação
 * da verificação de idade.
 */
@Component
class ConsultaDeContasPadrao implements ConsultaDeContas {

  private final RepositorioDeContas contas;
  private final RepositorioDeVerificacoes verificacoes;

  ConsultaDeContasPadrao(RepositorioDeContas contas, RepositorioDeVerificacoes verificacoes) {
    this.contas = contas;
    this.verificacoes = verificacoes;
  }

  @Override
  public Optional<ContaResumo> porId(ContaId id) {
    return contas.porId(id).map(conta -> conta.resumo(verificacoes.aprovada(id)));
  }

  @Override
  public Map<ContaId, ContaResumo> porIds(Collection<ContaId> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<Conta> lidas = contas.porIds(ids);
    if (lidas.isEmpty()) {
      return Map.of();
    }
    Set<ContaId> aprovadas = verificacoes.aprovadas(lidas.stream().map(Conta::id).toList());
    return lidas.stream()
        .map(conta -> conta.resumo(aprovadas.contains(conta.id())))
        .collect(Collectors.toUnmodifiableMap(ContaResumo::id, Function.identity()));
  }
}
