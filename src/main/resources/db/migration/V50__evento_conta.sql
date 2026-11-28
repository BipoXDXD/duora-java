-- Eventos de tempo real por conta (Etapa 4, E1): o stream SSE retoma pelo seq. A ordem por conta
-- segue o commit porque quem grava segura a trava consultiva da conta até o fim da transação.
-- Retenção de 1 dia por criado_em (RetencaoDeEventos); a exclusão da conta apaga pelo gancho.
create table evento_conta (
    seq bigint generated always as identity primary key,
    conta_id uuid not null references conta (id) on delete cascade,
    nome text not null,
    dados jsonb not null,
    criado_em timestamptz not null
);

create index evento_conta_conta_seq on evento_conta (conta_id, seq);
create index evento_conta_criado_em on evento_conta (criado_em);
