-- Pares que não voltam a ser pareados (Etapa 4, P4). Um par sem direção: conta_menor vem antes de
-- conta_maior na ordem do uuid (bytes sem sinal), a mesma do ParDeContas no Java. Um veto real
-- substitui CONECTADOS, nunca o contrário. Sem retenção: fica até a exclusão de uma das contas, que
-- apaga pelo gancho (a conta vira lápide e o cascade não dispara).
create table par_vetado (
    conta_menor uuid not null references conta (id) on delete cascade,
    conta_maior uuid not null references conta (id) on delete cascade,
    motivo text not null
        check (motivo in ('NAO', 'SAIDA', 'DENUNCIA', 'BLOQUEIO', 'CONECTADOS', 'SEM_DECISAO')),
    vetado_em timestamptz not null,
    primary key (conta_menor, conta_maior),
    check (conta_menor < conta_maior)
);

create index par_vetado_conta_maior on par_vetado (conta_maior);
