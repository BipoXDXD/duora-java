-- V5: sessões. O banco guarda só o SHA-256 do token do cookie. A renovação deslizante grava no
-- máximo uma vez a cada 24 h, por UPDATE condicional em renovada_em.
create table sessao (
    id          uuid        primary key,
    conta_id    uuid        not null references conta (id) on delete cascade,
    token_hash  bytea       not null unique,
    criada_em   timestamptz not null,
    expira_em   timestamptz not null,
    renovada_em timestamptz not null
);

-- Para revogar todas as sessões de uma conta.
create index sessao_conta_id on sessao (conta_id);

-- Para a retenção apagar as sessões vencidas.
create index sessao_expira_em on sessao (expira_em);
