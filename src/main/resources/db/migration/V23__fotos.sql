-- Fotos de perfil: no máximo uma por estado e conta (a aprovada vale até a pendente ser aprovada;
-- a recusada é só a marca da recusa). Os arquivos ficam no armazém de imagens, categoria perfil,
-- sob o mesmo id. O envio não exige perfil: nada aponta para a tabela perfil.
create table foto (
    id uuid primary key,
    conta_id uuid not null references conta(id) on delete cascade,
    estado text not null check (estado in ('PENDENTE', 'APROVADA', 'RECUSADA')),
    largura int not null check (largura between 1 and 1600),
    altura int not null check (altura between 1 and 1600),
    enviada_em timestamptz not null,
    decidida_em timestamptz,
    check ((estado = 'PENDENTE') = (decidida_em is null))
);

create unique index foto_uma_por_estado on foto (conta_id, estado);

-- Fila da moderação: pendentes mais antigas primeiro.
create index foto_fila on foto (enviada_em, id) where estado = 'PENDENTE';
