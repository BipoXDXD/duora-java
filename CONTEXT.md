# Duora

Rede social de encontros em que duas pessoas se conhecem fazendo algo juntas num cenário temático, antes de conversar. Este glossário fixa a linguagem do domínio; as decisões ficam em `docs/adr/`.

## Language

### Cenários

**Cenário**:
Lugar temático de um encontro (o terraço, o aquário…), com identidade permanente e uma atividade.
_Evitar_: mapa, fase, sala, cena

**Catálogo de cenários**:
O conjunto de todos os cenários que o produto conhece.
_Evitar_: registro, lista

**Cenário sorteável**:
Cenário do catálogo liberado para o sorteio e para o convite.
_Evitar_: ativo, publicado

**Cenário jogado**:
Cenário em que uma conta já teve um encontro aberto; alimenta o sorteio.
_Evitar_: visitado, histórico (como termo de domínio)

**Nome de exibição**:
Como o cenário aparece para as pessoas (o terraço é o "Terraço das Estrelas Cadentes"); escolhido pelo dono depois que o cenário existe.
_Evitar_: título (o título é o cabeçalho da revelação), apelido

**Atividade**:
O que o par faz no cenário: montagem ou jogo.
_Evitar_: modo, tipo

**Cenário de montagem**:
Cenário cuja atividade é montar uma composição a dois.
_Evitar_: cenário de composição

### Montagem

**Abertura**:
O ritual sem palavras que um cenário pode declarar no começo da montagem (no terraço, acender as lanternas a dois). Acontece uma vez por encontro e termina quando as duas pessoas tocam dentro da janela ou quando o tempo máximo passa; enquanto dura, ninguém mexe em peças.
_Evitar_: tutorial, introdução, aquecimento

**Zona**:
Grupo de lugares que aceita certas peças (centro, lateral).
_Evitar_: área, região

**Lugar**:
Posição nomeada da cena onde cabe uma peça.
_Evitar_: slot, casa, posição

**Peça**:
Objeto que um participante põe num lugar da sua zona.
_Evitar_: item, prop (prop é termo de asset)

**Peça obrigatória**:
Peça que precisa estar na cena para a composição ficar completa.
_Evitar_: principal

**Peça secundária**:
Peça de escolha; de cada pessoa entra uma só.
_Evitar_: opcional, extra

**Dono da peça**:
O assento a quem a peça pertence.
_Evitar_: autor

**Assento**:
O lado de um participante no encontro (A ou B).
_Evitar_: jogador, slot

**Composição**:
O arranjo atual de peças em lugares.
_Evitar_: tabuleiro, layout, montagem (montagem é a atividade)

**Limite antes do parceiro**:
Quantas peças alguém põe antes que o outro ponha a primeira.
_Evitar_: portão (como nome de conceito)

**Sinal**:
Sugestão de um participante para o outro mexer numa peça.
_Evitar_: dica, pedido

### Revelação

**Desfecho**:
A regra de cada cenário que transforma uma composição completa no resultado; sempre o mesmo resultado para a mesma composição.
_Evitar_: final, outcome. Não usar sozinho para o que o par decide depois do encontro (Etapa 5): isso é o **desfecho da decisão**.

**Resultado**:
O que o desfecho produziu: pares chave → valor, em ordem.
_Evitar_: placar, pontuação

**Revelação**:
O momento em que os dois confirmaram e o resultado aparece.
_Evitar_: fim, resolução

**Pedido de ajuste**:
Depois da revelação, o pedido de um participante para voltar à montagem. A montagem só volta quando a outra pessoa aceita; ela pode recusar, quem pediu pode cancelar o próprio pedido, e o pedido não expira. Não é um **sinal**: o sinal sugere mover uma peça durante a montagem.
_Evitar_: ajustar (sozinho, como nome), sinal, refazer

**Momento**:
Estado visual da cena: crepúsculo (montagem) e noite (revelação).
_Evitar_: horário, tema

**Legenda**:
Título e texto da revelação, montados com o resultado.
_Evitar_: descrição, resumo

**Efeito**:
Reação visual ou sonora de uma peça (ou de um valor do resultado) na revelação. Os efeitos de cada peça formam a sua revelação no espelho de apresentação; não existe uma segunda lista de efeitos.
_Evitar_: animação, FX

### Encontro

**Encontro**:
A sessão de um par num cenário, da entrada à decisão. Até a Etapa 5 ele ainda se chama "sala" no código.
_Evitar_: partida, jogo, sala (para o encontro em si)

**Sala**:
A partir da Etapa 4, o lugar de espera antes do encontro: a fila e o convite. Não é o encontro.
_Evitar_: lobby, espera

### Apresentação

**Espelho de apresentação**:
Textos, sprites, sons e efeitos de um cenário, sem regra; conferido contra o cenário do servidor.
_Evitar_: config, descrição do cenário

**Geometria**:
Onde ficam os lugares e os props no render de um cenário.
_Evitar_: cena (cena é o componente visual)

### Perfil

**Perfil**:
O que a pessoa mostra de si: apelido, pronomes, intenções, bio e foto. Pertence à conta, mas não é a conta.
_Evitar_: conta, cadastro (para o perfil)

**Intenção**:
Como a pessoa quer usar o Duora: amizade, namoro ou os dois. Ao menos uma.
_Evitar_: objetivo, modo

**Ajustes de busca**:
A faixa de idade e o raio que a pessoa escolhe para quem quer conhecer, e se o raio é obrigatório.
_Evitar_: filtros

**Preferências de pareamento**:
O que o pareamento lê de uma conta: intenções, ajustes de busca e, com namoro, o gênero próprio e os buscados. Fica só no servidor; nunca é serializada nem logada.
_Evitar_: ajustes (ajustes são o que a pessoa edita), filtros

**Dados de namoro**:
O gênero da pessoa e quem ela quer conhecer. Dado sensível (LGPD, art. 11): só com consentimento, só com a intenção namoro, só para o pareamento; some quando a pessoa desliga o namoro.
_Evitar_: preferências sexuais, interesses (sozinho)

**Orientação**:
Dado opcional e sensível, com consentimento próprio. Nunca entra no pareamento; aparece só para conexões e só se a pessoa escolher mostrar.
_Evitar_: sexualidade, preferência

**Pronomes**:
Como a pessoa quer ser chamada (ela/dela, ele/dele, elu/delu ou uma descrição curta). Opcionais; aparecem para quem a encontra.
_Evitar_: gênero (pronomes não são gênero)

**Célula**:
A área de cerca de 5 km (geohash de 5 caracteres) em que a localização vira; a coordenada é descartada. Só o pareamento a lê.
_Evitar_: localização exata, posição, coordenada

**Faixa de distância**:
O que a tela mostra no lugar da distância: até 5 km, até 20 km, até 50 km ou mais de 50 km, entre células.
_Evitar_: distância (sozinho), quilômetros

**Pausa**:
Tira a pessoa da fila de encontros sem mexer na conta nem no perfil; ela volta quando quiser.
_Evitar_: desativar, ocultar

**Selo verificado**:
A marca, no perfil, de que a verificação de idade da conta foi aprovada.
_Evitar_: verificado (sozinho, sem dizer de quê), conta verificada

**Projeção**:
O recorte do perfil para uma audiência: público (encontro), completo (conexão) e preferências de pareamento (servidor). Cada uma só tem o que aquela audiência pode ver (ADR 0008).
_Evitar_: DTO, visão

**Foto pendente**:
Foto enviada que ainda espera a moderação. Não aparece para ninguém além do titular; a aprovada anterior vale até a nova ser aprovada.
_Evitar_: foto em análise, rascunho

**Borrada**:
A variante da foto para quem ainda não é conexão: reduzida a 32 px e ampliada, sem detalhe a recuperar. A outra é a nítida.
_Evitar_: desfocada, miniatura
