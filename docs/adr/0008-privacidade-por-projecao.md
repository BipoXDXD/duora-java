# 0008 — Privacidade por projeção

- **Estado:** aceita
- **Data:** 2026-11-28
- **Etapa:** 3

## Contexto

A partir da Etapa 3, o perfil guarda dados de sensibilidade muito diferente no mesmo lugar: apelido e pronomes, que qualquer pessoa no encontro vê; idade, bio e intenções, que só uma conexão deveria ver; e gênero, interesses e orientação, dados sensíveis da LGPD (art. 11), que pedem consentimento próprio e nunca podem chegar a terceiros. A localização é outro caso: o pareamento precisa de proximidade, mas guardar ou mostrar onde a pessoa está é risco à segurança dela.

As etapas seguintes (fila, encontro, conexões, chat) vão ler o perfil de outras contas. Se cada uma montasse a própria leitura das tabelas, bastaria um `select *` esquecido ou um campo novo serializado por engano para vazar um dado sensível. A foto tem o mesmo problema: nítida, borrada ou nenhuma, conforme quem vê.

## Decisão

- **Localização só como célula geohash de 5 caracteres.** O servidor recebe a coordenada, converte em célula (cerca de 5 km) e a descarta: nunca a grava, loga nem ecoa. A tela mostra faixas de distância (`FaixaDeDistancia`), nunca a distância nem a célula. A célula só sai de `perfil` por `Perfis.celula`, para o pareamento.
- **Uma projeção por audiência.** O módulo `perfil` não expõe as linhas: expõe tipos que só têm o que cada audiência pode ver. `PerfilPublico` (apelido, foto borrada, pronomes, selo) é o encontro; `PerfilCompleto` (mais idade, intenções, bio, foto nítida) é a conexão; `Preferencias` (intenções, ajustes, gênero próprio e buscados) é o pareamento, só dentro do servidor, nunca serializada nem logada. As três saem de `Perfis`, montadas de uma leitura só (`LeituraDePerfis.ficha`), e só para conta `ATIVA`.
- **Gênero, interesses e orientação nunca a terceiros.** Gênero e interesses não existem em `PerfilPublico` nem em `PerfilCompleto`; servem só ao pareamento, via `Preferencias`. A orientação entra no `PerfilCompleto` só quando a pessoa marcou mostrá-la, e nunca no público. Os `toString` desses tipos omitem os dados sensíveis.
- **Mesma decisão para a foto e o perfil público, com o mesmo 404.** `AcessoAFoto.nivel(quemVe, titular)` decide as duas coisas: `NENHUM` esconde a foto e o perfil. Toda recusa é um 404 com corpo igual ao do recurso que não existe (`FOTO_INEXISTENTE`, `PERFIL_INEXISTENTE`): conta inexistente, não ativa, sem perfil ou escondida pelo acesso não se distinguem. As etapas de conexão, bloqueio e denúncia mudam só essa decisão.
- **Borrada derivada de 32 px.** A foto borrada é reduzida a 32 px no maior lado e ampliada de volta a 400 px: não há detalhe a recuperar, ao contrário de um desfoque aplicado sobre a imagem inteira. Ela pode ir para cache privado curto; a nítida nunca.

## Consequências

- Uma etapa nova que precise de um campo a mais do perfil muda a projeção da audiência dela, em vez de ler a tabela; o `ArquiteturaTest` já impede outros módulos de chegar a `perfil/dominio` ou `perfil/infra`.
- `PerfisApiTest` trava as quatro chaves exatas de `GET /api/perfis/{id}` e confere, com uma bio canária, que nem bio nem namoro aparecem no corpo.
- A célula de 5 caracteres limita a precisão do pareamento a uns 5 km; para encontros presenciais em cidade, a faixa "até 5 km" basta.
- A leitura do perfil junta cinco tabelas numa consulta; cada uma tem `conta_id` como chave, então o custo é o de cinco buscas por chave primária.

## Alternativas consideradas

- **Distância exata arredondada** (mostrar "a 3 km"): com algumas leituras de pontos diferentes, a trilateração acha a pessoa mesmo com arredondamento. Rejeitada: faixas sobre células não dão o que triangular.
- **Coordenada cifrada no banco:** protege o banco parado, mas o servidor continua tendo a coordenada para decifrar, e um vazamento do servidor (ou um log) a expõe. Guardar só a célula elimina o dado em vez de escondê-lo. Rejeitada.
- **Projeção única com nulos** (um tipo de perfil com os campos sensíveis nulos conforme quem vê): uma regra esquecida num lugar só deixa o campo preenchido e serializado. Com tipos separados, o campo nem existe na projeção errada. Rejeitada.
