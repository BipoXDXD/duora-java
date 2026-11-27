# 0006 — Retenção por política, num job diário único

- **Estado:** aceita
- **Data:** 2026-11-27
- **Etapa:** 2a

## Contexto

A partir da Etapa 2, o Duora acumula dados que não podem viver para sempre: links de acesso, sessões, mensagens da outbox, chaves de idempotência e registros de acesso (183 dias, Marco Civil art. 15). Depois vêm perfis, chats e imagens de encontro. Cada tipo tem um prazo e um instante de referência próprios, e a LGPD pede que nada fique além do necessário.

Espalhar um `@Scheduled` por módulo daria N horários, N jeitos de logar e de medir, e nenhum lugar que responda "o que o Duora apaga, e quando". Centralizar o `DELETE` num só lugar faria o núcleo conhecer a tabela de todos os módulos.

## Decisão

- **Cada módulo registra o que apaga.** Um bean de `retencao.PoliticaDeRetencao` por tipo de dado, no módulo dono: `nome()` em kebab-case único, `prazo()` positivo, `expirar(Instant limite)` apaga o que é anterior a `limite` (estrito) e devolve quantos, `tamanho()` diz quanto há acumulado. A política é idempotente e roda fora de transação.
- **Lotes de até 1000 linhas, cada um na própria transação.** A política fornece só o `DELETE` de um lote, num `TransactionTemplate`; o laço é `Lotes.apagarAteAcabar`, que chama o lote até ele devolver zero. Nenhuma política repete o laço, e nenhuma segura lock de tabela por minutos. Arquivos saem só depois do commit de cada lote.
- **Um job diário único.** `ExpiracaoDeDados.executar()` roda todas as políticas em ordem de nome, com `limite = agora - prazo`, às 03:30 de São Paulo (`duora.retencao.cron`, `AgendamentoDaRetencao`). O agendamento obedece a `duora.agendamentos.habilitados`, como o da outbox; os testes chamam `executar()` direto. Execuções simultâneas são serializadas (`synchronized`), e chamar dentro de uma transação falha na hora, porque uma transação de fora juntaria todos os lotes numa só.
- **Falha isolada.** Uma exceção numa política vira `ResultadoDaExpiracao(nome, 0, true)` e log ERROR com o nome e a classe do erro; as outras seguem. O que ela já apagou em lotes anteriores fica apagado, e o resto sai na execução seguinte.
- **Métrica por política.** Gauge `duora.retencao.tamanho{politica}` sobre `tamanho()` e contadores `duora.retencao.removidos{politica}` e `duora.retencao.falhas{politica}`. Um acúmulo que cresce ou uma falha que se repete aparecem sem ler log.
- **Log sem dado pessoal.** Cada execução tem um correlation ID próprio (UUID v7) no MDC. O log leva só nome, contagem e classe do erro, nunca a mensagem da exceção, que pode trazer e-mail ou token.
- **`retencao/` é só a raiz, sem web nem JDBC.** O pacote tem a interface, o laço e o job; o `DELETE` mora na `infra/` de cada módulo. O `ArquiteturaTest` trava as duas coisas: `retencaoSoNaRaiz` e a regra geral `dominioSemWebNemJdbc`.
- **Uma thread do agendador para cada tarefa.** O pool sobe de 2 para 3 (`spring.task.scheduling.pool.size`): despachante da outbox, varredor do terraço e retenção. Com 2 threads, uma retenção longa deixaria a outbox e o varredor dividindo uma só, e um lote lento da outbox (até 5 × 15 s de timeout) atrasaria o varredor. A thread extra fica parada 23 h por dia, o que custa pouco.

## Consequências

- Os módulos da Etapa 2a (links, sessões, outbox, chaves de idempotência, registros de acesso) e os das Etapas 3, 5 e 6 implementam `PoliticaDeRetencao`; o teste de contrato (P11) confere os prazos por `prazos()`.
- Um prazo zero ou negativo, um nome repetido ou fora de kebab-case derrubam o boot, em vez de apagar dado vivo ou misturar métricas.
- Cada `tamanho()` roda um `count` a cada leitura do gauge; com índice no instante de referência, é barato no volume do piloto.
- Com várias instâncias, cada uma rodaria o job; como as políticas são idempotentes e apagam em lotes curtos, o pior caso é trabalho repetido. Um lock distribuído entra se o volume pedir (Etapa 10).

## Alternativas consideradas

- **Política declarativa** (a política diz tabela, coluna e prazo, e o núcleo monta o `DELETE`): menos código por módulo, mas o núcleo passaria a ter JDBC e SQL montado com nome de tabela, e não cobriria o que foge do padrão: arquivos depois do commit, outbox que só apaga mensagem enviada ou falha, junções. Rejeitada.
- **Núcleo dono do laço e da transação** (`expirarLote(limite, maximo)` e o núcleo repete até zero): tira o laço das políticas, mas põe `TransactionTemplate` no núcleo e uma transação em volta de código que pode apagar arquivo. `Lotes.apagarAteAcabar` dá o mesmo reaproveitamento sem tirar a transação do módulo dono.
- **Um `@Scheduled` por módulo:** sem acoplamento, mas com N horários, sem isolamento uniforme de falha e sem métrica padrão.
