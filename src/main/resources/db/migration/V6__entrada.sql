-- V6: entrada por link mágico. O banco guarda só o SHA-256 do token; o consumo é um UPDATE
-- condicional em usado_em. Um pedido novo marca como usados os links abertos do mesmo e-mail.
create table link_de_entrada (
    id         uuid        primary key,
    email      text        not null check (email = lower(email)),
    token_hash bytea       not null unique,
    criado_em  timestamptz not null,
    expira_em  timestamptz not null,
    usado_em   timestamptz
);

-- Para invalidar os links abertos do e-mail e apagá-los na exclusão da conta.
create index link_de_entrada_email on link_de_entrada (email);

-- Para a retenção apagar os links vencidos.
create index link_de_entrada_expira_em on link_de_entrada (expira_em);

-- E-mail bloqueado, guardado só como HMAC-SHA256 da chave de bloqueio. A Etapa 8 amplia o check
-- com BANIMENTO.
create table email_bloqueado (
    chave_hmac bytea       primary key,
    motivo     text        not null check (motivo in ('MENOR')),
    criado_em  timestamptz not null
);
