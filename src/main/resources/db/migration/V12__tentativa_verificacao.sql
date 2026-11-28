-- V12: tentativas de verificação de idade, para o limite por dia e a faixa de dúvida. A reserva nasce
-- sem sessão (a sessão do fornecedor é criada fora da transação), ganha o sessao_id e depois o
-- resultado, junto com concluida_em; só a tentativa com sessão pode ser concluída. A política de
-- retenção apaga a tentativa 1 dia depois de criada_em; a exclusão da conta, na mesma transação.
create table tentativa_verificacao (
    id           uuid        primary key,
    conta_id     uuid        not null references conta (id) on delete cascade,
    finalidade   text        not null check (finalidade in ('SELFIE', 'DOCUMENTO')),
    sessao_id    text        unique,
    resultado    text        check (resultado in ('APROVADA', 'DUVIDA', 'RECUSADA', 'REPETIR')),
    criada_em    timestamptz not null,
    concluida_em timestamptz,
    check ((resultado is null) = (concluida_em is null)),
    check (concluida_em is null or sessao_id is not null)
);

-- Para contar as tentativas da conta nas últimas 24 h (e achar as da exclusão) sem varrer a tabela.
create index tentativa_verificacao_conta on tentativa_verificacao (conta_id, criada_em);

-- Para a retenção achar as tentativas vencidas sem varrer a tabela.
create index tentativa_verificacao_criada on tentativa_verificacao (criada_em);
