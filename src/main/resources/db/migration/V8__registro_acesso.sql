-- V8: registros de acesso (Marco Civil, art. 15): IP, porta de origem e instante de cada login e
-- renovação de sessão, guardados por 183 dias. Sem on delete cascade: a conta excluída vira lápide
-- e o registro fica pelo prazo legal; quem apaga é a política de retenção, pelo instante.
create table registro_acesso (
    id       uuid        primary key,
    conta_id uuid        references conta (id),
    ip       inet        not null,
    porta    int         check (porta between 0 and 65535),
    instante timestamptz not null,
    evento   text        not null check (evento in ('LOGIN', 'RENOVACAO'))
);

-- Para a retenção apagar os registros vencidos.
create index registro_acesso_instante on registro_acesso (instante);
