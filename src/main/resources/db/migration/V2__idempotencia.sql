-- V2: chaves de idempotência. A reserva entra antes do efeito, com resposta nula; o efeito grava a
-- resposta na própria transação. A PK (escopo, chave) é o que impede duas reservas da mesma chave.
create table chave_de_idempotencia (
    escopo       text        not null,
    chave        text        not null check (chave ~ '^[A-Za-z0-9-]{8,64}$'),
    impressao    text        not null,
    resposta     jsonb,
    criada_em    timestamptz not null,
    reservada_em timestamptz not null,
    primary key (escopo, chave)
);

-- Para a retenção apagar as chaves velhas por idade.
create index chave_de_idempotencia_criada_em on chave_de_idempotencia (criada_em);
