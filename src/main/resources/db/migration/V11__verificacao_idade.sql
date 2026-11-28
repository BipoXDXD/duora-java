-- V11: aprovação da verificação de idade, uma por conta. Só a aprovação fica guardada: o método e o
-- instante, nada da selfie nem do documento. A recusa vira lápide com e-mail bloqueado e a dúvida
-- vive só na tentativa (V12). A exclusão da conta apaga a linha na mesma transação; o cascade cobre
-- a remoção física da conta.
create table verificacao_idade (
    conta_id    uuid        primary key references conta (id) on delete cascade,
    metodo      text        not null check (metodo in ('ESTIMATIVA_FACIAL', 'DOCUMENTO')),
    aprovada_em timestamptz not null
);
