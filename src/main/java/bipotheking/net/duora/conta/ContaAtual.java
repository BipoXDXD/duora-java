package bipotheking.net.duora.conta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca o parâmetro {@link ContaId} de um handler protegido: recebe a conta da sessão. Por padrão a
 * rota exige o cadastro concluído; {@code cadastroConcluido = false} aceita também contas {@code
 * PENDENTE} e {@code AGUARDANDO_VERIFICACAO}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContaAtual {

  boolean cadastroConcluido() default true;
}
