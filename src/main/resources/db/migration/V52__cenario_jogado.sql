-- Cenários que cada conta já jogou (Etapa 4, P5): o sorteio prefere o cenário inédito para o par.
-- Uma linha por conta e cenário, com a primeira vez; jogar de novo não muda nada. Sem retenção:
-- fica até a exclusão da conta, que apaga pelo gancho (a conta vira lápide e o cascade não
-- dispara). A chave primária já serve à leitura por conta.
create table cenario_jogado (
    conta_id uuid not null references conta (id) on delete cascade,
    cenario_id text not null,
    primeira_vez_em timestamptz not null,
    primary key (conta_id, cenario_id)
);
