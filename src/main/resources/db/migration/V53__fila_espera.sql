-- Fila contínua dos encontros públicos (Etapa 4, P6): uma linha por conta enquanto ela espera ou
-- acabou de ser pareada. Sem preferência nem célula: o pareamento lê o perfil a cada ciclo. A linha
-- sai quando a pessoa desiste, entra no encontro ou fica ausente além do limite; a exclusão da conta
-- apaga pelo gancho (a conta vira lápide e o cascade não dispara).
create table fila_espera (
    conta_id uuid primary key references conta (id) on delete cascade,
    entrou_em timestamptz not null,
    de_teste boolean not null,
    ausente_desde timestamptz,
    encontro_id text,
    cenario_id text,
    pareado_em timestamptz,
    check ((encontro_id is null) = (pareado_em is null)
        and (encontro_id is null) = (cenario_id is null))
);

-- Posição e ordem do ciclo: só quem ainda espera, por ordem de chegada.
create index fila_espera_esperando on fila_espera (entrou_em, conta_id) where encontro_id is null;
